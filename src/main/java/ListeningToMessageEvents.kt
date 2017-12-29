import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.listeners.PresenceChangeListener
import com.ullink.slack.simpleslackapi.listeners.ReactionAddedListener
import com.ullink.slack.simpleslackapi.listeners.SlackChannelJoinedListener
import com.ullink.slack.simpleslackapi.listeners.SlackMessageDeletedListener
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import com.ullink.slack.simpleslackapi.listeners.SlackMessageUpdatedListener
import com.ullink.slack.simpleslackapi.listeners.UserTypingListener

import objects.SlackDetails

import java.util.ArrayList
import java.util.regex.Pattern

/**
 * Created by Mateusz on 23/05/2017.
 */
class ListeningToMessageEvents(private val session: SlackSession, private val databaseManager: DatabaseManager) {

    /**
     * This method shows how to register a listener on a SlackSession
     */

    /**
     * Used for purpose registering a listener which will be called when
     * user posts a message on the channel.

     * It also be used for purpose finding out who was mentioned in the message,
     * and making an array list out of it.

     *
     */
    fun registeringAListener() {
        val messagePostedListener = SlackMessagePostedListener { event, _ ->
            if (!event.sender.isBot) {
                val slackMessage = SlackDetails()
                if (!event.channel.isDirect) {

                    slackMessage.channelID = event.channel.id
                    slackMessage.message = event.messageContent
                    slackMessage.userID = event.sender.id
                    slackMessage.slackMessageID = event.timestamp
                    slackMessage.userTimezoneLabel = event.sender.timeZoneLabel
                    slackMessage.userTimezoneOffset = convertTimeOff(event.sender.timeZoneOffset)
                    slackMessage.userTimezoneOffsetMilisec = event.sender.timeZoneOffset
                    slackMessage.threadID = event.threadTimestamp
                    channelMessageReceived(slackMessage)
                } else {
                    slackMessage.userID = event.sender.id
                    slackMessage.channelID = event.channel.id
                    slackMessage.message = event.messageContent
                    slackMessage.teamID = session.team.id
                    slackMessage.slackUser = event.sender
                    val userIneraction = UserInteraction(databaseManager)
                    userIneraction.directMessageReceived(slackMessage, session)
                }
            }
        }
        session.addMessagePostedListener(messagePostedListener)
    }


    private fun channelMessageReceived(slackMessage: SlackDetails) {
        // detect both patterns: <@U12345678> and <@U12345678|username>
        val mentionsPattern = Pattern.compile("<@([^<>\\|]{9})(\\|)?([^>]*)>")
        val mentionsMatcher = mentionsPattern.matcher(slackMessage.message)
        // these lists are used to replace mentions


        val startIndexes = ArrayList<Int>()
        val endIndexes = ArrayList<Int>()
        val replaces = ArrayList<String>()
        while (mentionsMatcher.find()) {
            startIndexes.add(mentionsMatcher.start())
            endIndexes.add(mentionsMatcher.end())
            val slackUserId = mentionsMatcher.group(1)
            replaces.add(slackUserId)
        }
        slackMessage.replaces = replaces


        databaseManager.saveMessageInDatabase(slackMessage)
    }


    fun messageEdited() {
        val slackMessage = SlackDetails()
        val messageEdited = SlackMessageUpdatedListener { event, _ ->
            slackMessage.message = event.newMessage
            slackMessage.slackMessageID = event.messageTimestamp
            slackMessage.channelID = event.channel.id


            // detect both patterns: <@U12345678> and <@U12345678|username>
            val mentionsPattern = Pattern.compile("<@([^<>\\|]{9})(\\|)?([^>]*)>")
            val mentionsMatcher = mentionsPattern.matcher(slackMessage.message)
            // these lists are used to replace mentions


            val startIndexes = ArrayList<Int>()
            val endIndexes = ArrayList<Int>()
            val replaces = ArrayList<String>()
            while (mentionsMatcher.find()) {
                startIndexes.add(mentionsMatcher.start())
                endIndexes.add(mentionsMatcher.end())
                val slackUsername = mentionsMatcher.group(1)
                replaces.add(slackUsername)
            }
            slackMessage.replaces = replaces
            databaseManager.messageEdited(slackMessage)
        }
        session.addMessageUpdatedListener(messageEdited)
    }

    fun messageDeleted() {
        val slackMessage = SlackDetails()
        val messageDeleted = SlackMessageDeletedListener { event, _ ->
            slackMessage.slackMessageID = event.messageTimestamp
            databaseManager.messageDeleted(slackMessage)
        }
        session.addMessageDeletedListener(messageDeleted)
    }

    /**
     * Used for purpose registering a listener which will be called when
     * user's status changes i.e. AWAY or ONLINE
     * It will also check if the user is already added in the slackUser table,
     * if not the user will be added to that table by collecting his userID from user table,
     * using the email

     *
     */
    fun registeringLoginListener() {

        val slackUserChangeListener = PresenceChangeListener { event, _ ->
            val databaseSlackUsers = databaseManager.selectAllSlackUsersFromDatabase()
            session.refetchUsers()
            val slackUser = session.findUserById(event.userId)
            if (!slackUser.isBot) {
                val slackMessage = SlackDetails()
                slackMessage.userID = slackUser.id
                slackMessage.userTimezoneOffset = convertTimeOff(slackUser.timeZoneOffset)
                slackMessage.userTimezoneOffsetMilisec = slackUser.timeZoneOffset
                slackMessage.userTimezoneLabel = slackUser.timeZoneLabel
                slackMessage.slackUser = slackUser

                val userInteraction = UserInteraction(databaseManager)
                var firstLogin = false
                if (!databaseSlackUsers.contains(slackMessage.userID)) {
                    val userEmail = slackUser.userMail
                    val userNickName = slackUser.userName
                    databaseManager.insertUserToSlackUserTable(userEmail, slackMessage.userID, userNickName)
                    databaseSlackUsers.add(slackMessage.userID)
                    firstLogin=true
                    userInteraction.sendWelcomeMessage(session,slackUser)
                    println("${slackMessage.userID} first Login")
                }
                if (event.presence.name == "ACTIVE" && !firstLogin) {
                    Thread({
                        println("${slackMessage.userID} status changed to active")
                        userInteraction.checkUserActivityTracker(slackMessage, session)
                    }).start()
                }
                slackMessage.email = slackUser.userMail
                slackMessage.status = event.presence.toString()
                databaseManager.saveStatusInDatabase(slackMessage)
            }
        }
        session.addPresenceChangeListener(slackUserChangeListener)
    }

    /**
     * Used for purpose registering a listener which will be called when
     * bot is added to a channel that it hasn't seen before.

     *
     */
    fun registeringChannelCreatedListener() {


        val slackChannelJoinedListener = SlackChannelJoinedListener { slackChannelJoined, slackSession ->
            val slackMessage = SlackDetails()
            slackMessage.slackSideID = slackSession.team.id
            slackMessage.channelID = slackChannelJoined.slackChannel.id
            slackMessage.channelName = slackChannelJoined.slackChannel.name

            databaseManager.newChannelCreated(slackMessage)

        }
        session.addChannelJoinedListener(slackChannelJoinedListener)
    }

    /***
     * Used for purposen
     */
    fun userTyping() {

        val slackUserTyping = UserTypingListener { userTyping, _ ->
            val slackMessage = SlackDetails()
            slackMessage.userID = userTyping.user.id
            slackMessage.channelID = userTyping.channel.id
            slackMessage.teamID = session.team.id
            slackMessage.userTimezoneOffsetMilisec = userTyping.user.timeZoneOffset
            slackMessage.userTimezoneOffset = convertTimeOff(userTyping.user.timeZoneOffset)
            slackMessage.userTimezoneLabel = userTyping.user.timeZoneLabel
            databaseManager.userTypingEvent(slackMessage)
        }
        session.addUserTypingListener(slackUserTyping)
    }

    fun reaction() {
        val reaction = ReactionAddedListener { reactionMessage, _ ->
            val slackMessage = SlackDetails()
            slackMessage.userID = reactionMessage.user.id
            slackMessage.emojiName = reactionMessage.emojiName
            slackMessage.userTimezoneOffsetMilisec = reactionMessage.user.timeZoneOffset
            slackMessage.userTimezoneLabel = reactionMessage.user.timeZoneLabel
            slackMessage.userTimezoneOffset = convertTimeOff(reactionMessage.user.timeZoneOffset)
            slackMessage.slackMessageID = reactionMessage.messageID
            println(slackMessage)
            databaseManager.messageReaction(slackMessage)
        }
        session.addReactionAddedListener(reaction)
    }

    fun convertTimeOff(timeOffSet: Int): Double {
        return timeOffSet.toDouble() / 3600
    }

}


