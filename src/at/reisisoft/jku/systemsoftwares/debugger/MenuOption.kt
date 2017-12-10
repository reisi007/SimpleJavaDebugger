package at.reisisoft.jku.systemsoftwares.debugger

enum class MenuOption {
    BREAKPOINT_LIST,
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