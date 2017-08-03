import com.ullink.slack.simpleslackapi.*
import com.ullink.slack.simpleslackapi.SlackAttachment
import com.ullink.slack.simpleslackapi.SlackPreparedMessage
import objects.SlackDetails
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule


/**
 * Created by Mateusz on 23/05/2017.
 */
class UserInteraction {



    /***
     * The function is used for purpose of sending a welcome message to the user
     *
     * @param session user for purpose of interacting with Slack
     * @param user user for purpose of sending message to correct user
     *
     */
    fun sendDirectWelcomeMessage(session: SlackSession,slackUser : SlackUser, databaseManager: DatabaseManager){
        var slackChannel : String =""

        val allUsers : ArrayList<SlackDetails> = databaseManager.getUserGroup()

        allUsers.filter { it.userID==slackUser.id }
                .forEach { slackChannel = it.channelID }
        var welcomeMessage: String=""
        synchronized(this) {
            welcomeMessage = databaseManager.readWelcomeMessage(slackChannel)
        }
        val welcomeMessageBuilder = SlackPreparedMessage.Builder()
                .withMessage(welcomeMessage)
                .build()

        session.sendMessageToUser(slackUser,welcomeMessageBuilder)
        databaseManager.setupActivityTracker(slackUser)
        remindToCheckTimeZone(session,slackUser)
    }

    /***
     * Used to send a tutorial survey with instructions
     *
     *  @param user used for purpose of checking user details on database
     *  @param session user for purpose of interacting with Slack
     *
     */
    fun startTutorialSurvey(session: SlackSession,user: SlackUser,databaseManager: DatabaseManager){
        sendSurveyMessage(session,user,"Dummy",databaseManager)
    }

    /***
     * The function is used for purpose of sending a survey message to a user
     *
     * @param session user for purpose of interacting with Slack
     * @param user user for purpose of sending message to correct user
     *
     */
    fun sendSurveyMessage(session: SlackSession,user: SlackUser,surveyName : String,databaseManager: DatabaseManager){

        val question = databaseManager.getSurveyQuestion(surveyName,user.id)
        var answers : String = String()
        var questionNumber : Int =0
        for(answer in question.questionAnswers){
            questionNumber++
            answers= "$answers\n$questionNumber $answer"
        }
        val slackMessage = SlackAttachment()

        slackMessage.title=question.questionText
        slackMessage.text=answers
        val preparedMessage = SlackPreparedMessage.Builder()
                .addAttachment(slackMessage)
                .build()
        session.sendMessageToUser(user,preparedMessage)
    }

    /***
     * The function is used for purpose of remiding user to check their timezone
     *
     * @param session user for purpose of interacting with Slack
     * @param user user for purpose of sending message to correct user
     *
     */
    fun remindToCheckTimeZone(session: SlackSession,user:SlackUser){

        val slackMessage = SlackAttachment()

        slackMessage.title="Timezone check."
        slackMessage.text="Your time zone is currently set to ${convertTimeOff(user.timeZoneOffset)}, ${user.timeZoneLabel}. Is this correct? \n1. Yes \n2. No"
        val timeZoneMessage = SlackPreparedMessage.Builder()
                .addAttachment(slackMessage)
                .build()
        session.sendMessageToUser(user,timeZoneMessage)

    }



    /***
     * Used to send a tutorial survey with instructions
     *
     *  @param user used for purpose of checking user details on database
     *  @param session user for purpose of interacting with Slack
     *
     */
    fun calculateSurveyScoreForUser(session: SlackSession,user: SlackUser){

    }

    /***
     * The purpose of this method is to keep track of what stage the user is currently at.
     *
     * @param slackMessage used to store user details
     * @param keys used to store database details
     * @param session used for communication with slack
     *
     */
    fun directMessageReceived(slackMessage : SlackDetails, keys: Keys,session: SlackSession){

        val databaseManager = DatabaseManager(keys)
        val currentActivity = currentUserActivity(slackMessage,databaseManager)
        when(currentActivity){
            0 -> print("all actvities complted")//todo decide on what to do here
            1 -> checkMessageForTime(slackMessage,session,databaseManager,currentActivity)
            2 -> checkDummySurveyAnswer(slackMessage,session,databaseManager,"Dummy")
            3 -> print("surveyMBTI")
            4 -> print("surveyPAEI")
            5 -> print("surveyVAK")
        }
    }

    fun checkUserActivityTracker(slackDetails: SlackDetails,keys: Keys,session: SlackSession){
        val databaseManager = DatabaseManager(keys)
        val currentActivity = currentUserActivity(slackDetails,databaseManager)
        when(currentActivity){
            0 -> print("all actvities complted")//todo decide if to ignore this
            1 -> remindToCheckTimeZone(session,slackDetails.slackUser)
            2 -> print("surveyDummy")
            3 -> print("surveyMBTI")
            4 -> print("surveyPAEI")
            5 -> print("surveyVAK")
        }
    }

    fun currentUserActivity(slackMessage : SlackDetails,databaseManager : DatabaseManager) : Int{
        val userActivity = databaseManager.checkUserActivityStage(slackMessage)
        return userActivity.currentActivity()
    }

    fun checkDummySurveyAnswer(slackDetails: SlackDetails, session : SlackSession, database : DatabaseManager, currentActivity : String){
        val totalNumberOFOptions = database.totalNumberOfOptions(currentActivity,slackDetails.userID)
        val message = slackDetails.message.toLowerCase().replace(" ", "")
        var isMessageANumber : Boolean
        try {
            message.toDouble()
            isMessageANumber=true
        } catch(e: NumberFormatException) {
            isMessageANumber=false
        }

        if(isMessageANumber){
            val intMessage = message.toInt()
            if(intMessage<1 || intMessage> totalNumberOFOptions[0]){
                val messageToSend = "Your answer needs to be between 1 and $totalNumberOFOptions"
                val messageBuilder = SlackPreparedMessage.Builder()
                        .withMessage(messageToSend)
                        .build()
                session.sendMessageToUser(slackDetails.slackUser,messageBuilder)
            }else{
                for (i in 1..totalNumberOFOptions[0]){
                    if(intMessage==i){

                    }
                }
            }

        }else{
            val messageToSend = "Incorrect value please type numerical value for example 1 or 2"
            val messageBuilder = SlackPreparedMessage.Builder()
                    .withMessage(messageToSend)
                    .build()
            session.sendMessageToUser(slackDetails.slackUser,messageBuilder)
        }

    }

    fun updateSurveyTable(){
        //todo create a row in dummy table and provide answer to a question
    }

    fun checkMessageForTime(slackDetails: SlackDetails, session : SlackSession, database : DatabaseManager, currentActivity : Int){
        val message = slackDetails.message.toLowerCase().replace(" ", "")
        val timer = Timer("schedule", true)
        if(message=="1" || message =="yes"){
            val messageToSend = "Great! We can move on to the next step now"

            val messageBuilder = SlackPreparedMessage.Builder()
                    .withMessage(messageToSend)
                    .build()

            session.sendMessageToUser(slackDetails.slackUser,messageBuilder)
            database.updateActivityTracker(currentActivity, slackDetails.userID)


            timer.schedule(2000) {
                startTutorialSurvey(session,slackDetails.slackUser,database)
            }

        }else if(message == "2" || message == "no"){
            val messageToSend = "Please click on this link to update your time zone: https://sciencegsd.slack.com/account/settings#timezone"

            val messageBuilder = SlackPreparedMessage.Builder()
                    .withMessage(messageToSend)
                    .build()
            session.sendMessageToUser(slackDetails.slackUser,messageBuilder)



            timer.schedule(5000) {
                remindToCheckTimeZone(session,slackDetails.slackUser)
            }

        }else{
            val messageToSend = "Incorrect value please type 1 or 2"

            val messageBuilder = SlackPreparedMessage.Builder()
                    .withMessage(messageToSend)
                    .build()
            session.sendMessageToUser(slackDetails.slackUser,messageBuilder)
        }
    }

    fun convertTimeOff(timeOffSet: Int): Double {
        return timeOffSet.toDouble() / 3600
    }

}