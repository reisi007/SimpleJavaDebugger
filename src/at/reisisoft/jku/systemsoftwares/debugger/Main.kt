package at.reisisoft.jku.systemsoftwares.debugger;

import com.sun.jdi.*
import com.sun.jdi.event.*
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.StepRequest
import java.io.*
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList

object Main {
    private val menuOptions = MenuOption.values().asList()

    private val activeBreakpoints: MutableSet<BreakpointRequest> = TreeSet({ a, b -> a.location().compareTo(b.location()) })

    fun VirtualMachine.getMainThread(): ThreadReference? {
        return this.allThreads().stream().filter { it.name() == "main" }.findAny().orElseGet { null }
    }

    private val input = BufferedReader(InputStreamReader(System.`in`))

    private fun BufferedReader.getIntInPositiveRange(): Int {
        return getIntInRange(0, Integer.MAX_VALUE)
    }

    private fun BufferedReader.getIntInRange(fromIncluding: Int, toExcluding: Int): Int {
        try {
            val v = Integer.parseInt(readLine())
            if (v in fromIncluding..(toExcluding - 1))
                return v
            throw RuntimeException()
        } catch (e: RuntimeException) {
            println("This is not a valid number, please try again.")
            return getIntInRange(fromIncluding, toExcluding);
        }
    }

    private val defaultClassName = "at.testitest.Test"
    private var programToDebug = ""
    @JvmStatic
    fun main(args: Array<String>) {

        programToDebug = if (args.isNotEmpty()) args[0] else defaultClassName

        val con = Bootstrap.virtualMachineManager().launchingConnectors()[0]

        val arguments = con.defaultArguments()

        arguments.get("main")?.setValue(programToDebug)
                ?: throw IllegalStateException("No \"main\" argument found!")
        arguments.get("suspend")?.setValue("true")
                ?: throw IllegalStateException("No \"suspend\" argument found!")

        var cp = Paths.get("out").resolve("artifacts").resolve("javaDebugger_jar").toAbsolutePath().toString() + "\\*"

        cp = '\"' + cp + '\"'
        println("Classpath: " + cp)
        arguments.get("options")?.setValue("-classpath $cp")
        println(arguments)

        val vm = con.launch(arguments)
        val vmProcess = vm?.process() ?: throw IllegalStateException()

        Redirection(vmProcess.errorStream, System.err).start()
        Redirection(vmProcess.inputStream, System.out).start()


        val eventRequestManager = vm.eventRequestManager()
        val methodEntryRequest = eventRequestManager.createMethodEntryRequest()
        methodEntryRequest.addClassFilter(programToDebug)
        methodEntryRequest.enable()

        vm.resume() // Will resume until method entry of main method

        val eventQueue = vm.eventQueue()
        try {
            while (true) {
                handleEvents(eventQueue, vm)
            }
        } catch (e: VMDisconnectedException) {
            System.out.println("Debuggee finished execution during debugger menu. Sorry for the inconvenience!")
        }
    }

    private fun handleEvents(eventQueue: EventQueue, vm: VirtualMachine) {
        val eventSet: EventSet = eventQueue.remove()
        for (event in eventSet) {
            when (event) {
                is VMStartEvent ->
                    println("Started debugging!")
                is MethodEntryEvent -> {
                    vm.eventRequestManager().deleteEventRequest(event.request())
                    menu(vm)
                }
                is StepEvent -> {
                    handleStep(event, vm)
                    menu(vm)
                }
                is VMDisconnectEvent -> {
                    println("Debugee terminated")
                    System.exit(0)
                }
                is VMDeathEvent -> println("Debugee VM died")
                is BreakpointEvent -> {
                    handleBreakPoint(event)
                    menu(vm)
                }
                else -> TODO("Event handler for event ${event.javaClass} does not exist!")
            }
        }
    }

    private fun menu(vm: VirtualMachine) {
        //Normal program flow
        var curOption: MenuOption
        do {// Handle events
            printMenu()
            //normal program flow
            curOption = getMenuOptionFromInt(input.getIntInRange(0, menuOptions.size))
            when (curOption) {
                MenuOption.EXIT -> {
                    println("Goodbye!")
                    System.exit(0)
                }
                MenuOption.BREAKPOINT_DELETE -> deleteBreakPoints(input, vm)
                MenuOption.RUN_STEP -> vm.getMainThread()?.let { stepOver(it, vm) }
                MenuOption.PRINT_STACKTRACE ->
                    vm.getMainThread()?.let { printStackTrace(it.frames()) } ?: kotlin.run { Main.println("StackTrace not found") }
                MenuOption.RUN_TO_BREAKPOINT -> {
                    System.out.println("Run to next breakpoint")
                    vm.resume()
                }
                MenuOption.BREAKPOINT_SET -> setBreakpoint(input, vm)
                MenuOption.PRINT_VARIABLES -> vm.getMainThread()?.let {
                    val frames = it.frames()
                    frames.getOrNull(0)?.let { printVariables(it) }
                } ?: kotlin.run { println("No StackFrame found. Unable to print variables") }
            }
        } while (curOption != MenuOption.RUN_STEP && curOption != MenuOption.RUN_TO_BREAKPOINT)
    }

    private fun printMenu() {
        for (i in 1..2) {
            System.out.println()
        }
        println("Welcome to Debugger for Java Version -2")
        println("Please choose one of the following commands to continue:")
        println()
        menuOptions.forEach { System.out.println("${it.ordinal} --> $it") }
        print("Your selection: ")
    }

    private fun getMenuOptionFromInt(ordinal: Int): MenuOption {
        return menuOptions.get(ordinal);
    }

    private fun stepOver(threadReference: ThreadReference, vm: VirtualMachine) {
        val stepRequest = vm.eventRequestManager().createStepRequest(threadReference, StepRequest.STEP_LINE, StepRequest.STEP_OVER)
        stepRequest.addCountFilter(1)
        stepRequest.enable()
        vm.resume()
    }

    private fun printStackTrace(stackFrames: List<StackFrame>) {
        stackFrames.forEach { System.out.println("${it.location()}, (Thread: ${it.thread().name()})") }

    }

    private fun deleteBreakPoints(input: BufferedReader, vm: VirtualMachine) {
        println("Delete currently active breakpoints:")
        System.out.println()
        System.out.println("-1 -> exit this menu without deleting a breakpoint")
        val breakpointRequestList: List<BreakpointRequest> = ArrayList(activeBreakpoints)
        if (breakpointRequestList.size == 0)
            System.out.println("No breakpoints are currently set")
        else
            breakpointRequestList.forEachIndexed { index, request ->
                val location = request.location()
                System.out.println("$index -> $location")
            }
        System.out.print("Your input: ")
        val selection = input.getIntInRange(-1, activeBreakpoints.size)
        breakpointRequestList.getOrNull(selection)?.let { requestToDelete ->
            if (activeBreakpoints.remove(requestToDelete)) {
                vm.eventRequestManager().deleteEventRequest(requestToDelete)
                println("Deleted breakpoint at position ${requestToDelete.location()}")
            } else throw IllegalAccessError("Non coherent state in breakpoints....")
        } ?: kotlin.run { System.out.println("No breakpoint was deleted...") }
    }

    private fun handleStep(e: StepEvent, vm: VirtualMachine) {
        println("[STEP] Location: ${e.location().lineNumber()} in ${e.location().method()}")
        printVariables(e.thread().frame(0))
        vm.eventRequestManager().deleteEventRequest(e.request())
    }

    private fun handleBreakPoint(e: BreakpointEvent) {
        println("[BREAKPOINT] Location: ${e.location().lineNumber()} in ${e.location().method()}")
        printVariables(e.thread().frame(0))
    }

    private fun printVariables(frame: StackFrame) {
        frame.visibleVariables().forEach { v ->
            println("${v.typeName()} ${v.name()} = ${valueAsString(frame.getValue(v))}")
        }
    }

    private fun valueAsString(value: Value): String {
        if (value is ArrayReference)
            return value.values.joinToString(" , ", "Array [ ", " ]") { valueAsString(it) }
        else if (value is ObjectReference && !(value is StringReference)) {
            return value.getValues(value.referenceType().allFields()).map {
                "${it.key.typeName()} ${it.key.name()} = ${valueAsString(it.value)};"
            }.joinToString(" ", "${value.referenceType().sourceName()} { ", " }")
        } else return value.toString()
    }


    private fun setBreakpoint(input: BufferedReader, vm: VirtualMachine) {
        vm.allClasses().stream().map { it.javaClass.name }.filter { it.startsWith("at") }.forEach { System.out.println(it) }
        print("In which class should the breakpoint be set? [$programToDebug]: ")
        val line = input.readLine()
        val finalClassName = (if (line.length == 0) programToDebug else line).trim()
        println("Looking for methods in $finalClassName")
        try {
            val breakPointInClass = vm.classesByName(finalClassName)[0]
            val allLineLocations: List<Location> = breakPointInClass.allLineLocations()
            val fromLine = allLineLocations.first().lineNumber()
            val toLine = allLineLocations.last().lineNumber()
            println("Breakpoints can be set in lines between $fromLine and $toLine")
            var success: Boolean;
            do {
                print("Line number [$fromLine..$toLine]: ")
                val lineNumber = input.getIntInRange(fromLine, toLine + 1)
                val location: Location? = allLineLocations.asSequence().filter { it.lineNumber() == lineNumber }.firstOrNull()

                if (location != null) {
                    success = true
                    val breakpointRequest = vm.eventRequestManager().createBreakpointRequest(location)
                    val addResult = activeBreakpoints.add(breakpointRequest)
                    if (addResult)
                        breakpointRequest.enable()
                    else
                        throw IllegalStateException("A breakpoint at this position already is set!")

                } else {
                    success = false
                }

            } while (!success)
            println("Breakpoint set successfully!")
        } catch (e: RuntimeException) {
            println("Not able to set breakpoint: ${e.message}")
        }

    }

    private fun println(value: Any) {
        System.out.println("== $value ==")
    }
}

enum class MenuOption {
    BREAKPOINT_DELETE,
    BREAKPOINT_SET,
    RUN_TO_BREAKPOINT,
    RUN_STEP,
    PRINT_STACKTRACE,
    PRINT_VARIABLES,
    EXIT;

    override fun toString(): String {
        return super.toString().replace('_', ' ').toLowerCase()
    }
}

internal class Redirection(`is`: InputStream, os: OutputStream) : Thread() {
    private val `in`: Reader
    private val out: Writer

    init {
        `in` = InputStreamReader(`is`)
        out = OutputStreamWriter(os)
    }

    override fun run() {
        try {
            val buf = CharArray(1024)
            var n = `in`.read(buf, 0, 1024)
            while (n >= 0) {
                out.write(buf, 0, n)
                n = `in`.read(buf, 0, 1024)
            }
            out.flush()
        } catch (e: IOException) {
        }
    }
}