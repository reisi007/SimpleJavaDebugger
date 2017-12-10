package at.reisisoft.jku.systemsoftwares.debugger;

import com.sun.jdi.*
import com.sun.jdi.event.*
import com.sun.jdi.request.EventRequestManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths

object Main {
    val menuOptions = MenuOption.values().asList()

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

    @JvmStatic
    fun main(args: Array<String>) {
        val programToDebug = if (args.isNotEmpty()) args[0] else defaultClassName

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
        val vmProcess = vm.process()

        Redirection(vmProcess.errorStream, System.err).start()
        Redirection(vmProcess.inputStream, System.out).start()


        //Normal program flow
        var curOption: MenuOption;
        BufferedReader(InputStreamReader(System.`in`)).use { input ->
            do {// Handle events
                val requestManager = vm.eventRequestManager()
                val eventQueue: EventQueue? = vm.eventQueue()
                eventQueue?.let { handleEvents(it) }
                printMenu()
                //normal program flow
                curOption = getMenuOptionFromInt(input.getIntInRange(0, menuOptions.size))
                when (curOption) {
                    MenuOption.EXIT -> println("Goodbye!")
                    MenuOption.START_DEBUGGEE -> vm.resume()
                    MenuOption.BREAKPOINT_SET -> setBreakpoint(input, requestManager, vm)
                    else -> TODO("Option $curOption is not supported")

                }
                //Set event handler
                vm.eventQueue()

            } while (curOption != MenuOption.EXIT)
        }
        System.exit(0)
    }

    private fun handleEvents(eventQueue: EventQueue) {
        val eventSet: EventSet = eventQueue.remove(50)
        for (event in eventSet) {
            when (event) {
                is VMStartEvent -> println("Started debugging!")
                is VMDeathEvent -> println("Debugee VM died")
                is BreakpointEvent -> handleBreakPoint(event)
                else -> TODO("Event handler for event ${event.javaClass} does not exist!")
            }
        }
    }

    private fun printMenu() {
        println("Welcome to Debugger for Java Version -2")
        println("Please choose one of the following commands to continue:")
        println()
        menuOptions.forEach { println("${it.ordinal} --> $it") }
        print("Your selection: ")
    }

    private fun getMenuOptionFromInt(ordinal: Int): MenuOption {
        return menuOptions.get(ordinal);
    }

    private fun handleBreakPoint(e: BreakpointEvent) {
        println("[BREAKPOINT] Locatioon: ${e.location().lineNumber()} in ${e.location().method()}")
        printVariables(e.thread().frame(0))
    }

    private fun printVariables(frame: StackFrame) {
        frame.visibleVariables().forEach { v ->
            println("${v.typeName()} ${v.name()} = ${valueAsString(frame.getValue(v))}")
        }
    }

    private fun valueAsString(value: Value): String {
        if (value is ArrayReference)
            return value.values.joinToString(" , ", "Array [ ", " ]") {
                valueAsString(it)
            }
        else if (value is ClassObjectReference && !(value is StringReference)) {
            val allFields = value.reflectedType().allFields()
            return value.getValues(allFields).map {
                "${it.key.typeName()} ${it.key.name()} = ${valueAsString(it.value)}${System.lineSeparator()}"
            }.joinToString(",", "${value.reflectedType().sourceName()} { ", " }")
        } else return value.toString()
    }

    private val defaultClassName = "at.testitest.Test"
    private fun setBreakpoint(input: BufferedReader, requestManager: EventRequestManager, vm: VirtualMachine) {
        vm.allClasses().stream().map { it.javaClass.name }.filter { it.startsWith("at") }.forEach { System.out.println(it) }
        print("In which class should the breakpoint be set? [$defaultClassName]: ")

        val line = input.readLine()
        println("Available variables:")

        val finalClassName = (if (line.length == 0) defaultClassName else line).trim()
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
                    val breakpointRequest = requestManager.createBreakpointRequest(location)
                    breakpointRequest.enable()
                } else {
                    success = false
                }

            } while (!success)

        } catch (e: RuntimeException) {
            println("Not able to set breakpoint: ${e.message}")
        }

    }
}