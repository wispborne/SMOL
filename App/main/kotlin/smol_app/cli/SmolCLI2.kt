//package smol_app.cli
//
//import com.github.ajalt.clikt.output.TermUi
//import kotlinx.cli.*
//import kotlinx.coroutines.runBlocking
//import smol_access.business.UserManager
//import smol_access.business.UserModProfileManager
//
//@OptIn(ExperimentalCli::class)
//@Deprecated("Use SmolCLI")
//class SmolCLI2(
//    private val userManager: UserManager,
//    private val userModProfileManager: UserModProfileManager
//) {
//    fun parse(args: List<String>) {
//        kotlin.runCatching {
//            Smol()
//                .parse(args.toTypedArray())
//        }
//            .onFailure {
//                TermUi.echo(it.message)
////                TermUi.echo(chainOfCommand.getFormattedHelp())
//            }
//    }
//
//    inner class Smol : ArgParser(programName = "smol") {
//        init {
//            subcommands(
//                Help(),
//                ModProfileList(),
//                ModProfileCreate(),
//                ModProfileSet(),
//                ModProfileRemove()
//            )
//        }
//    }
//
//    inner class Help : Subcommand(name = "help", actionDescription = "Show this message and exit") {
//        override fun execute() {
//            TermUi.echo(helpMessage)
//        }
//    }
//
//    inner class ModProfileList : Subcommand(name = "modprofile-list", actionDescription = "List all mod profiles") {
//        override fun execute() {
//            TermUi.echo(userManager.getUserProfile().modProfiles.joinToString { it.toString() })
//        }
//    }
//
//    inner class ModProfileCreate :
//        Subcommand(name = "modprofile-create", actionDescription = "Create a new mod profile") {
//        val profileName by option(ArgType.String).required()
//        val description by option(ArgType.String)
//        val sortOrder by option(ArgType.Int)
//
//        override fun execute() {
//            val profile = userManager.createModProfile(name = name, description = description, sortOrder = sortOrder)
//            TermUi.echo("Created mod profile $profile (not yet active).")
//        }
//    }
//
//    inner class ModProfileSet : Subcommand(name = "modprofile-set", actionDescription = "Set active mod profile") {
//        val profileId by option(ArgType.Int).required()
//
//        override fun execute() {
//            runBlocking { userModProfileManager.switchModProfile(profileId) }
//            val profile = userManager.getUserProfile().modProfiles.single { it.id == profileId }
//            TermUi.echo("Changed mod profile to $profile.")
//        }
//    }
//
//    inner class ModProfileRemove : Subcommand(name = "modprofile-remove", actionDescription = "Remove a mod profile") {
//        val profileId by option(ArgType.Int).required()
//
//        override fun execute() {
//            runBlocking { userManager.removeModProfile(profileId) }
//            TermUi.echo("Removed mod profile $profileId.")
//        }
//    }
//}