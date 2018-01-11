import com.ullink.slack.simpleslackapi.*
import com.ullink.slack.simpleslackapi.SlackAttachment
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import objects.SlackDetails
import surveyCalculators.MBTI
import surveyCalculators.PAEI
import surveyCalculators.VARK
import java.util.ArrayList
import kotlin.collections.HashMap
import java.util.HashSet




/**
 * Created by Mateusz on 23/05/2017.
 */
class UserInteraction(private val databaseManager: DatabaseManager) {


    /***
     * The function is used for purpose of sending a welcome message to the user
     * The message is being collected from a database slackChannel table
     *
     * @param session user for purpose of interacting with Slack
     * @param slackUser user for purpose of sending message to correct user
     *
     *
     */
    fun sendWelcomeMessage(session: SlackSession, slackUser: SlackUser) {
        var slackChannel = ""

        val allUsers: ArrayList<SlackDetails> = databaseManager.getUserGroup()

        for (item in allUsers) {
            if (item.userID == slackUser.id) {
                slackChannel = item.channelID
                println(slackChannel)
                break
            }
        }
        var welcomeMessage = ""
        synchronized(this) {
            println("slack Channel is $slackChannel")
            welcomeMessage = databaseManager.readWelcomeMessage(slackChannel)
            println("welcome message is $welcomeMessage")
        }
        val welcomeMessageBuilder = SlackPreparedMessage.Builder()
                .withMessage(welcomeMessage)
                .withUnfurl(false)
                .build()
        session.sendMessageToUser(slackUser, welcomeMessageBuilder)
        println("Welcome message sent to ${slackUser.id}")
        databaseManager.setupActivityTracker(slackUser)
    }

    /***
     * Used to send a tutorial survey with instructions
     *
     *  @param user used for purpose of checking user details on database
     *  @param session user for purpose of interacting with Slack
     *
     */
    private fun startTutorialSurvey(session: SlackSession, user: SlackUser) {
        sendSurveyMessage(session, user, "dummy")
    }

    /***
     * The function is used for purpose of sending a survey message to a user
     *
     * @param session user for purpose of interacting with Slack
     * @param user user for purpose of sending message to correct user
     *
     */
    private fun sendSurveyMessage(session: SlackSession, user: SlackUser, surveyName: String) {
        val surveyQuestionAnswer = databaseManager.getSurveyQuestion(surveyName, user.id)
        var answers = String()
        var questionNumber = 0
        val charArray: CharArray = charArrayOf('A', 'B', 'C', 'D')
        when (surveyName) {
            "dummy", "mbti", "paei" -> {
                for (answer in surveyQuestionAnswer.questionAnswers) {
                    questionNumber++
                    answers = "$answers\n$questionNumber. ${answer.capitalize()}"
                }
            }
            "vark" -> {
                for (answer in surveyQuestionAnswer.questionAnswers) {
                    val index = surveyQuestionAnswer.questionAnswers.indexOf(answer)
                    answers = "$answers\n${charArray[index]}. ${answer.capitalize()}"
                }
                answers = "$answers\nE. none of the options above"
            }
        }
        val slackMessage = SlackAttachment()
        slackMessage.title = "${surveyQuestionAnswer.questionNumber}. ${surveyQuestionAnswer.questionText.capitalize()}"
        slackMessage.text = answers
        val preparedMessage = SlackPreparedMessage.Builder()
                .withUnfurl(true)
                .addAttachment(slackMessage)
                .build()
        session.sendMessageToUser(user, preparedMessage)
    }

    /***
     * Send's user a reminder to fill in survey
     */
    private fun reminderToFillInSurvey(session: SlackSession, slackDetails: SlackDetails, surveyID: Int, surveyName: String) {

        var numberOfReminders = databaseManager.selectNumberOfRemindersAvailable(slackDetails.slackUser.id)
        numberOfReminders -= 1
        val slackMessage = SlackAttachment()
        if (numberOfReminders != 0) {
            val surveyReminderMessage = SlackPreparedMessage.Builder()
                    .addAttachment(slackMessage)
                    .withUnfurl(false)
                    .build()
            slackMessage.title = "Survey Reminder."
            var times = "times"
            if (numberOfReminders == 1) {
                times = "time"
            }
            slackMessage.text = "I am still waiting for you to respond back to my last question :white_frowning_face: . " +
                    "I will remind you $numberOfReminders more $times. Then I will move to next survey."
            databaseManager.reduceNumberOfReminders(slackDetails.slackUser.id)
            session.sendMessageToUser(slackDetails.slackUser, surveyReminderMessage)
        } else {
            val surveyReminderMessage = SlackPreparedMessage.Builder()
                    .addAttachment(slackMessage)
                    .withUnfurl(false)
                    .build()
            slackMessage.title = "Survey Reminder reached zero."
            slackMessage.text = "The amount of reminder reached 0, I am sorry but we will have to move to the next survey."
            databaseManager.updateActivityTracker(surveyID, slackDetails.slackUser.id)

            databaseManager.resetNumberOfReminders(slackDetails.slackUser.id)

            session.sendMessageToUser(slackDetails.slackUser, surveyReminderMessage)

            startNewSurvey(slackDetails, session, surveyName)
        }
    }

    /***
     * The function is used for purpose of reminding user to check their timezone
     *
     * @param session user for purpose of interacting with Slack
     * @param user user for purpose of sending message to correct user
     *
     */
    private fun remindToCheckTimeZone(session: SlackSession, user: SlackUser) {

        val slackMessage = SlackAttachment()

        slackMessage.title = "Timezone check."
        slackMessage.text = "Your time zone is currently set to ${convertTimeOff(user.timeZoneOffset)}, ${user.timeZoneLabel}. " +
                "Is this correct? \n1. Yes \n2. No"
        val timeZoneMessage = SlackPreparedMessage.Builder()
                .addAttachment(slackMessage)
                .withUnfurl(false)
                .build()
        session.sendMessageToUser(user, timeZoneMessage)
        println("Timezone reminder sent to ${user.id}")
    }

    private fun calculateSurveyScoreForUser(session: SlackSession, slackDetails: SlackDetails, surveyName: String) {
        var answers: ArrayList<Int>
        var result: HashMap<String, Int> = HashMap()
        val slackMessage = SlackAttachment()

        when (surveyName) {
            "mbti" -> {
                answers = databaseManager.selectAllTheAnswersBasedOnUserID(surveyName, slackDetails.userID)
                val mbti = MBTI(answers)
                result = mbti.mbtiResult()
                var letters = ""
                var letters2 = ""
                var numA = 0
                var numB = 0
                numA = result["e"] as Int
                numB = result["i"] as Int
                when {
                    numA == numB -> {
                        letters += "E"
                        letters2 += "I"
                    }
                    numA > numB -> {
                        letters += "E"
                        letters2 += "E"
                    }
                    else -> {
                        letters += "I"
                        letters2 += "I"
                    }
                }
                numA = result["s"] as Int
                numB = result["n"] as Int

                when {
                    numA == numB -> {
                        letters += "S"
                        letters2 += "N"
                    }
                    numA > numB -> {
                        letters += "S"
                        letters2 += "S"
                    }
                    else -> {
                        letters += "N"
                        letters2 += "N"
                    }
                }
                numA = result["t"] as Int
                numB = result["f"] as Int
                when {
                    numA == numB -> {
                        letters += "T"
                        letters2 += "F"
                    }
                    numA > numB -> {
                        letters += "T"
                        letters2 += "T"
                    }
                    else -> {
                        letters += "F"
                        letters2 += "F"
                    }
                }
                numA = result["j"] as Int
                numB = result["p"] as Int
                when {
                    numA == numB -> {
                        letters += "J"
                        letters2 += "P"
                    }
                    numA > numB -> {
                        letters += "J"
                        letters2 += "J"
                    }
                    else -> {
                        letters += "P"
                        letters2 += "P"
                    }
                }
                slackMessage.title = "Results"
                if (letters != letters2) {
                    letters = letters + " and  " + letters2
                }
                val resultOrder: String = "E(${result["e"]})\tS(${result["s"]})\tT(${result["t"]})\tJ(${result["j"]})\n" +
                        "I(${result["i"]})\tN(${result["n"]})\tF(${result["f"]})\tP(${result["p"]})\n" +
                        "\n" +
                        "You are $letters\n" +
                        "\n" +
                        "PDF with full explanation: https://gsd.mdx.ac.uk/gsdforms/mbti.pdf"
                slackMessage.text = resultOrder
                val preparedMessage = SlackPreparedMessage.Builder()
                        .addAttachment(slackMessage)
                        .withUnfurl(false)
                        .build()
                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)
            }
            "paei" -> {
                answers = databaseManager.selectAllTheAnswersBasedOnUserID(surveyName, slackDetails.userID)
                val ri = PAEI()

                result = ri.getScore(answers) as HashMap<String, Int>

                slackMessage.title = "Results"
                slackMessage.text = "P(${result["p"]}), A(${result["a"]}), E(${result["e"]}), I(${result["i"]}))"

                var preparedMessage = SlackPreparedMessage.Builder()
                        .addAttachment(slackMessage)
                        .withUnfurl(false)
                        .build()
                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)

                slackMessage.title = "Producer (Paei) (${result["p"]})"
                slackMessage.text = "Producers focus on producing results for customers and the organization.\n" +
                        "These people usually have two qualities:\n" +
                        "Technical knowledge of what needs to be done, and\n" +
                        "Persistence and Drive to see it through"
                preparedMessage = SlackPreparedMessage.Builder()
                        .addAttachment(slackMessage)
                        .withUnfurl(false)
                        .build()

                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)
                slackMessage.title = "Administrator (pAei) (${result["a"]})"
                slackMessage.text = "They usually focus on systems, policies, procedures and processes within the organization.\n" +
                        "They come to work on time, usually quiet and careful with the choice of words.\n" +
                        "They keep low key and like to use facts to prove their point.  They manage by the book.\n" +
                        "Administrators are usually analytical and detailed people and as such they pay attention to detail, follow through and think in organized ways.\n" +
                        "They are good in developing and follow through systems.\n" +
                        "Therefore, they tend to correct problems by adding new rules, policies, systems or procedures."
                preparedMessage = SlackPreparedMessage.Builder()
                        .addAttachment(slackMessage)
                        .withUnfurl(false)
                        .build()
                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)

                slackMessage.title = "Entrepreneur (paEi) (${result["e"]})"
                slackMessage.text = "Entrepreneurs, like to focus on future opportunities and threats, what changes to make, longer term and the big picture and can generate new ideas to improve methods, products and business easily.\n" +
                        "They provide energy for, and insights to, needed change for the organization.\n" +
                        "They are catalysts for needed changes in organization.\n" +
                        "Their style is creative, charismatic.\n" +
                        "They are creative, flexible, courageous and comfortable with risk and ambiguity.\n" +
                        "They have the ability to see things that others cannot see plus the willingness to believe in their “foresights” and undertake significant risks."
                preparedMessage = SlackPreparedMessage.Builder()
                        .addAttachment(slackMessage)
                        .withUnfurl(false)
                        .build()
                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)

                slackMessage.title = "Integrator (paeI) (${result["i"]})"
                slackMessage.text = "They focus on feelings, people and relationships issues (morale, cultural, consensus, etc.).\n" +
                        "They are good in helping to connect and build teamwork to get Producers (P), Administrators (A) and Entrepreneurs (E) and Integrators (I) to work synergistically for the organization.\n" +
                        "They are sensitive to people, the climate and group processes in their place of work.\n" +
                        "Therefore, Integrators tend to be excellent communicators and are good at listening.\n" +
                        "They are slow-paced, open, caring and usually show interest in other people’s personal lives.\n" +
                        "They tend to make up their mind when the team consensus is clear.\n" +
                        "Their offices are warm and inviting and have lots of people pictures and other mementos."
                preparedMessage = SlackPreparedMessage.Builder()
                        .addAttachment(slackMessage)
                        .withUnfurl(false)
                        .build()
                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)

            }
            "vark" -> {
                answers = databaseManager.selectAllTheAnswersBasedOnUserID(surveyName, slackDetails.userID)
                result = databaseManager.getAllVarkAnswers(answers)

                slackMessage.title = "Visual (Vark) (${result["v"]})"
                slackMessage.text = "Someone with a Visual learning style has a preference for seen or observed things, including pictures, diagrams, demonstrations, displays, handouts, films, flip-chart, etc. \n" +
                        "These people will use phrases such as ‘show me’, ‘let’s have a look at that’ and will be best able to perform a new task after reading the instructions or watching someone else do it first." +
                        " These are the people who will work from lists and written directions and instructions."

                var preparedMessage = SlackPreparedMessage.Builder()
                        .addAttachment(slackMessage)
                        .withUnfurl(false)
                        .build()
                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)

                slackMessage.title = "Aural (vArk) (${result["a"]})"
                slackMessage.text = "Someone with an Aural learning style has a preference for the transfer of information through listening: to the spoken word, of self or others, of sounds and noises.\n" +
                        "These people will use phrases such as ‘tell me’, ‘let’s talk it over’ and will be best able to perform a new task after listening to instructions from an expert.\n" +
                        "These are the people who are happy being given spoken instructions over the telephone, and can remember all the words to songs that they hear!"

                preparedMessage = SlackPreparedMessage.Builder()
                        .addAttachment(slackMessage)
                        .withUnfurl(false)
                        .build()
                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)

                slackMessage.title = "Read/write (vaRk) (${result["r"]})"
                slackMessage.text = "This preference is for information displayed as words. Not surprisingly, many teachers and students have a strong preference for this mode.\n" +
                        " Being able to write well and read widely are attributes sought by employers of graduates.\n" +
                        " This preference emphasizes text-based input and output – reading and writing in all its forms but especially manuals, reports, essays and assignments.\n" +
                        " People who prefer this modality are often addicted to PowerPoint, the Internet, lists, diaries, dictionaries, thesauri, quotations and words, words, words…\n" +
                        " Note that most PowerPoint presentations and the Internet, GOOGLE and Wikipedia are essentially suited to those with this preference as there is seldom an auditory channel or a presentation that uses Visual symbols."
                preparedMessage = SlackPreparedMessage.Builder()
                        .addAttachment(slackMessage)
                        .withUnfurl(false)
                        .build()
                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)

                slackMessage.title = "Kinaesthetic (varK) (${result["k"]})"
                slackMessage.text = "Someone with a Kinaesthetic learning style has a preference for physical experience - touching, feeling, holding, doing, practical hands-on experiences. \n" +
                        "These people will use phrases such as ‘let me try’, ‘how do you feel?’ and will be best able to perform a new task by going ahead and trying it out, learning as they go.\n" +
                        " These are the people who like to experiment, hands-on, and never look at the instructions first!\n" +
                        "People commonly have a main preferred learning style, but this will be part of a blend of all four.\n" +
                        " Some people have a very strong preference; other people have a more even mixture of two or less commonly, three styles.\n" +
                        "When you know your preferred learning style(s) you understand the type of learning that best suits you.\n" +
                        " This enables you to choose the types of learning that work best for you. There is no right or wrong learning style.\n" +
                        " The point is that there are types of learning that are right for your own preferred learning style."
                preparedMessage = SlackPreparedMessage.Builder()
                        .addAttachment(slackMessage)
                        .withUnfurl(false)
                        .build()
                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)
//                session.sendMessageToUser(slackDetails.slackUser, preparedMessage)
//
//
//                slackMessage.title = "Results"
//                slackMessage.text = "V(${result["v"]}), A(${result["a"]}), R(${result["r"]}), K(${result["k"]})"



            }
        }
        databaseManager.setTally(slackDetails.userID, surveyName, result)
    }

    /***
     * The purpose of this method is to keep track of what stage the user is currently at.
     *
     * @param slackMessage used to store user details
     * @param session used for communication with slack
     *
     */
    fun directMessageReceived(slackMessage: SlackDetails, session: SlackSession) {
        synchronized(this) {
            val currentActivityID = currentUserActivity(slackMessage)
            when (currentActivityID) {
                0 -> allActivitiesCompleted(session, slackMessage.slackUser)
                1 -> checkMessageForTime(slackMessage, session, currentActivityID)
                2 -> checkSurveyAnswer(slackMessage, session, "dummy", currentActivityID)
                3 -> checkSurveyAnswer(slackMessage, session, "mbti", currentActivityID)
                4 -> checkSurveyAnswer(slackMessage, session, "paei", currentActivityID)
                5 -> checkSurveyAnswer(slackMessage, session, "vark", currentActivityID)
            }
        }
    }

    fun checkUserActivityTracker(slackDetails: SlackDetails, session: SlackSession) {

        val currentActivity = currentUserActivity(slackDetails)
        if (currentActivity != 0) {
            when (currentActivity) {
                1 -> remindToCheckTimeZone(session, slackDetails.slackUser)
                else -> {
                    var surveyName = ""
                    when (currentActivity) {
                        2 -> surveyName = "dummy"
                        3 -> surveyName = "mbti"
                        4 -> surveyName = "paei"
                        5 -> surveyName = "vark"
                    }

                    reminderToFillInSurvey(session, slackDetails, currentActivity, surveyName)

                }
            }
        }
    }

    private fun currentUserActivity(slackDetails: SlackDetails): Int {
        val userActivity = databaseManager.checkUserActivityStage(slackDetails)
        return userActivity.currentActivity()
    }

    private fun checkSurveyAnswer(slackDetails: SlackDetails, session: SlackSession, surveyName: String, surveyID: Int) {
        /*collects total number of options available for survey
        collects total number of Questions
        collects what question the user is currently on*/

        when (surveyName) {
            "dummy", "mbti", "paei" -> {
                val values = databaseManager.totalNumberOfOptions(surveyName, slackDetails.userID)
                //push the message to lowercase and replaces any spaces with no space
                val message = slackDetails.message.toLowerCase().replace(" ", "")
                //checks if message is a number

                val isMessageANumber = try {
                    message.toDouble()
                    true
                } catch (e: NumberFormatException) {
                    false
                }

                if (isMessageANumber) {
                    //converts message to a number value
                    val intMessage = message.toInt()
                    //checks if the message is in range of the survey options
                    if (intMessage > values[0] || intMessage < 0) {

                        val messageToSend = "Your answer needs to be between 1 and ${values[0]}, or type 0 to return to previous question"
                        val messageBuilder = SlackPreparedMessage.Builder()
                                .withMessage(messageToSend)
                                .withUnfurl(false)
                                .build()
                        //notifies the user that the value is not in range of options
                        session.sendMessageToUser(slackDetails.slackUser, messageBuilder)
                    } else {
                        if (intMessage == 0) {
                            previousQuestion(slackDetails, session)
                        } else {
                            databaseManager.updateLastAnsweredQuestionColumn(slackDetails.userID)

                            if (values[1] >= values[2]) {
                                if (surveyName != "Dummy") {
                                    databaseManager.setAnswer(slackDetails.userID, surveyName, values[1], intMessage)
                                    calculateSurveyScoreForUser(session, slackDetails, surveyName)
                                }
                                databaseManager.updateActivityTracker(surveyID, slackDetails.userID)
                                databaseManager.resetNumberOfReminders(slackDetails.slackUser.id)

                                startNewSurvey(slackDetails, session, surveyName)
                            } else {
                                if (surveyName != "Dummy") {
                                    databaseManager.setAnswer(slackDetails.userID, surveyName, values[1], intMessage)
                                }
                                databaseManager.resetNumberOfReminders(slackDetails.slackUser.id)
                                sendSurveyMessage(session, slackDetails.slackUser, surveyName)
                            }
                        }
                    }
                } else {
                    val messageToSend = "Incorrect value please type numerical value between 1 and ${values[0]}, or type 0 to return to previous question"
                    val messageBuilder = SlackPreparedMessage.Builder()
                            .withMessage(messageToSend)
                            .withUnfurl(false)
                            .build()
                    session.sendMessageToUser(slackDetails.slackUser, messageBuilder)
                }
            }
            "vark" -> {
                //removing all non number or letter chars other than ABCDE
                val message = slackDetails.message
                        .toLowerCase()
                        .replace(" ", "")
                if (message == "0") {
                    previousQuestion(slackDetails, session)
                } else {
                    val count = isFormatValid(message)

                    if (!message.contains(Regex("(aa+|bb+|cc+|dd+|ee+|a+0|b+0|c+0|d+0|a+e|b+e|c+e|d+e|e+a|e+b|e+c|e+d)|[^a-e -]|(0-9)"))&&!count) {
                        val values = databaseManager.totalNumberOfOptions(surveyName, slackDetails.userID)
                        //val currentQuestion = databaseManager.selectCurrentUserQuestion(slackDetails.slackUser.id)
                        val score = VARK().selectQuestion(values[1], message)
                        databaseManager.varkAnswer(values[1], score, slackDetails.slackUser)
                        databaseManager.updateLastAnsweredQuestionColumn(slackDetails.userID)
                        databaseManager.resetNumberOfReminders(slackDetails.slackUser.id)

                        if (values[1] >= values[2]) {
                            calculateSurveyScoreForUser(session, slackDetails, surveyName)
                            databaseManager.updateActivityTracker(surveyID, slackDetails.userID)
                            startNewSurvey(slackDetails, session, surveyName)
                        } else {
                            sendSurveyMessage(session, slackDetails.slackUser, surveyName)
                        }
                    } else {
                        val messageToSend = "Incorrect value please select ABCD values, or type 0 to return to previous question."
                        val messageBuilder = SlackPreparedMessage.Builder()
                                .withMessage(messageToSend)
                                .withUnfurl(false)
                                .build()
                        session.sendMessageToUser(slackDetails.slackUser, messageBuilder)
                    }
                }
            }
        }
    }

    fun isFormatValid(input: String): Boolean {
        var a=0
        var b=0
        var c=0
        var d=0
        var e=0

        for (i in 0 until input.length) {
            when(input[i]){
                'a' -> a++
                'b' -> b++
                'c' -> c++
                'd' -> d++
                'e' -> e++
            }
            if(a==2||b==2||c==2||d==2||e==2){
               return true
            }
        }
        return false
    }

    private fun previousQuestion(slackDetails: SlackDetails, session: SlackSession) {
        val previousQuestion = databaseManager.selectCurrentUserQuestion(slackDetails.userID) - 1
        if (previousQuestion == 0) {
            val slackMessage = SlackAttachment()
            slackMessage.title = "Cannot go back"
            slackMessage.text = "This is first question, you cannot return to previous question."
            val timeZoneMessage = SlackPreparedMessage.Builder()
                    .addAttachment(slackMessage)
                    .withUnfurl(false)
                    .build()
            session.sendMessageToUser(slackDetails.slackUser, timeZoneMessage)
        } else {
            databaseManager.decreaseLastAnsweredQuestionColumn(slackDetails.userID)

            val currentActivityID = currentUserActivity(slackDetails)
            var surveyName = ""
            when (currentActivityID) {
                0 -> allActivitiesCompleted(session, slackDetails.slackUser)
                2 -> surveyName = "dummy"
                3 -> surveyName = "mbti"
                4 -> surveyName = "paei"
                5 -> surveyName = "vark"
            }
            databaseManager.setPreviousValueNull(slackDetails.userID, surveyName, previousQuestion)
            sendSurveyMessage(session, slackDetails.slackUser, surveyName)
        }
    }

    private fun startNewSurvey(user: SlackDetails, slackSession: SlackSession, previousSurveyName: String) {
        val currentActivityID = currentUserActivity(user)
        when (currentActivityID) {
            0 -> allActivitiesCompleted(slackSession, user.slackUser)
            3 -> surveyMBTIIntro(user, slackSession, previousSurveyName)
            4 -> surveyPAEIIntro(user, slackSession, previousSurveyName)
            5 -> surveyVARKIntro(user, slackSession, previousSurveyName)
        }
    }

    private fun surveyMBTIIntro(user: SlackDetails, session: SlackSession, previousSurveyName: String) {
        val slackMessage = SlackAttachment()

        slackMessage.title = "MBTI survey intro."
        slackMessage.text = "Well done on completing your ${previousSurveyName.toUpperCase()} survey, We are now going to move to MBTI Survey!\n" +
                "This questionnaire consists of a number of statements that will help you self-assess your MBTI score.\n" +
                "\n" +
                "Several hints about how to best complete this survey:\n" +
                "\n" +
                "There are no right answers to any of these questions.\n" +
                "Answer the questions quickly, do not over-analyze them. Some seem worded poorly. Go with what feels best.\n" +
                "Answer the questions as “the way you are”, not “the way you’d like to be seen by others"
        val timeZoneMessage = SlackPreparedMessage.Builder()
                .addAttachment(slackMessage)
                .withUnfurl(false)
                .build()
        databaseManager.createSurveyRow(user.slackUser, "MBTI")
        session.sendMessageToUser(user.slackUser, timeZoneMessage)

        sendSurveyMessage(session, user.slackUser, "mbti")
    }

    private fun surveyPAEIIntro(user: SlackDetails, session: SlackSession, previousSurveyName: String) {
        val slackMessage = SlackAttachment()

        slackMessage.title = "PAEI survey intro."
        slackMessage.text = "Well done on completing your ${previousSurveyName.toUpperCase()}, We are now going to move to PAEI Survey!\n" +
                "This questionnaire consists of a number of statements that will help you self-assess your PAEI score based on Adize's model of leadership styles.\n" +
                "\n" +
                "Try to think of how to actually act and what you are most comfortable with. \n" +
                "\n" +
                "You may only select one of the two options for each state`ent." +
                "\n" +
                "\n" +
                "It's typical for me..."
        val timeZoneMessage = SlackPreparedMessage.Builder()
                .addAttachment(slackMessage)
                .withUnfurl(false)
                .build()
        databaseManager.createSurveyRow(user.slackUser, "PAEI")
        session.sendMessageToUser(user.slackUser, timeZoneMessage)
        sendSurveyMessage(session, user.slackUser, "paei")
    }

    private fun surveyVARKIntro(user: SlackDetails, session: SlackSession, previousSurveyName: String) {
        val slackMessage = SlackAttachment()

        slackMessage.title = "VARK survey intro."
        slackMessage.text = "Well done on completing your ${previousSurveyName.toUpperCase()}, We are now going to move to VARK Survey!\n" +
                "Choose the answer which best explains your preference, by typing the letter assigned to it.\n" +
                "Please type more than one letter if a single answer does not match your perception.\n" +
                "Type E to any question that does not apply."
        val introMessage = SlackPreparedMessage.Builder()
                .addAttachment(slackMessage)
                .withUnfurl(false)
                .build()
        databaseManager.createSurveyRow(user.slackUser, "VARK")
        session.sendMessageToUser(user.slackUser, introMessage)
        sendSurveyMessage(session, user.slackUser, "vark")
    }

    private fun checkMessageForTime(slackDetails: SlackDetails, session: SlackSession, currentActivity: Int) {
        val message = slackDetails.message.toLowerCase().replace(" ", "")
        if (message == "1" || message == "yes") {
            val messageToSend = "Great! We can move on to the next step now"

            val messageBuilder = SlackPreparedMessage.Builder()
                    .withMessage(messageToSend)
                    .withUnfurl(false)
                    .build()

            session.sendMessageToUser(slackDetails.slackUser, messageBuilder)
            databaseManager.updateActivityTracker(currentActivity, slackDetails.userID)


            startTutorialSurvey(session, slackDetails.slackUser)


        } else if (message == "2" || message == "no") {
            val messageToSend = "Please click on this link to update your time zone: https://sciencegsd.slack.com/account/settings#timezone"

            val messageBuilder = SlackPreparedMessage.Builder()
                    .withMessage(messageToSend)
                    .withUnfurl(false)
                    .build()
            session.sendMessageToUser(slackDetails.slackUser, messageBuilder)




            remindToCheckTimeZone(session, slackDetails.slackUser)


        } else {
            val messageToSend = "Incorrect value please type 1 or 2"

            val messageBuilder = SlackPreparedMessage.Builder()
                    .withMessage(messageToSend)
                    .withUnfurl(false)
                    .build()
            session.sendMessageToUser(slackDetails.slackUser, messageBuilder)
        }
    }

    private fun convertTimeOff(timeOffSet: Int): String {
        val hours = timeOffSet / 3600
        val minutes = timeOffSet % 3600 / 60
        var symbol = '+'
        if (timeOffSet < 0) {
            symbol = '-'
        }


        var stringHours: String = ""
        if (hours < 10) {
            stringHours = "0${Math.abs(hours)}"
        } else {
            stringHours = Math.abs(hours).toString()
        }

        var stringMinutes: String = ""
        if (minutes < 10) {
            stringMinutes = "0$minutes"
        } else {
            stringMinutes = minutes.toString()
        }


        val values: String = "UTC $symbol$stringHours:$stringMinutes"
        return values
    }

    private fun allActivitiesCompleted(session: SlackSession, user: SlackUser) {
        val slackMessage = SlackAttachment()

        slackMessage.title = "Thanks for the message."
        slackMessage.text = "Hey ${user.realName} you have completed all of your activities! " +
                "You may now go back to your GSD project! Enjoy :)"
        val timeZoneMessage = SlackPreparedMessage.Builder()
                .addAttachment(slackMessage)
                .withUnfurl(false)
                .build()
        session.sendMessageToUser(user, timeZoneMessage)
    }


}