import com.ullink.slack.simpleslackapi.*
import com.ullink.slack.simpleslackapi.listeners.*

import java.util.ArrayList
import java.util.regex.Pattern

/**
 * Created by Mateusz on 23/05/2017.
 */
class ListeningToMessageEvents(var keys: Keys) {

    /**
     * This method shows how to register a listener on a SlackSession
     */

    /**
     * Used for purpose registering a listener which will be called when
     * user posts a message on the channel.

     * It also be used for purpose finding out who was mentioned in the message,
     * and making an array list out of it.

     * @param session from Slack
     */
    fun registeringAListener(session: SlackSession) {

        val messagePostedListener = SlackMessagePostedListener { event, _ ->
            event.channel.isDirect
            if (!event.sender.isBot) {
                val slackMessage = SlackDetails()
                slackMessage.channelID = event.channel.id
                slackMessage.message = event.messageContent
                slackMessage.userID = event.sender.id
                slackMessage.slackMessageID = event.timestamp
                slackMessage.userTimezoneLabel = event.sender.timeZoneLabel
                slackMessage.userTimezoneOffset = convertTimeOff(event.sender.timeZoneOffset)
                slackMessage.userTimezoneOffsetMilisec = event.sender.timeZoneOffset
                slackMessage.threadID = event.threadTimestamp



                if (slackMessage.message.contains("Hey Scanner", true)) {
                    val respondToUser = RespondToUser()
                    respondToUser.respondToUser(session, slackMessage.channelID)
                }

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

                val databaseManager = DatabaseManager(keys)
                databaseManager.saveMessageInDatabase(slackMessage)
            }
        }
        session.addMessagePostedListener(messagePostedListener)
    }

    fun messageEdited(session: SlackSession) {
        val slackMessage = SlackDetails()
        val messageEdited = SlackMessageUpdatedListener { event, _ ->
            slackMessage.message = event.newMessage
            slackMessage.slackMessageID = event.messageTimestamp
            slackMessage.channelID=event.channel.id


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
            val databaseManager = DatabaseManager(keys)
            databaseManager.messageEdited(slackMessage)
        }
        session.addMessageUpdatedListener(messageEdited)
    }

    fun messageDeleted(session: SlackSession) {
        val slackMessage = SlackDetails()
        val messageDeleted = SlackMessageDeletedListener { event, _ ->
            slackMessage.slackMessageID=event.messageTimestamp
            val databaseManager = DatabaseManager(keys)
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

     * @param session from Slack
     */
    fun registeringLoginListener(session: SlackSession) {
        val databaseManager = DatabaseManager(keys)


        val slackUserChangeListener = PresenceChangeListener { presenceChange, _ ->
            val databaseSlackUsers = databaseManager.selectAllSlackUsersFromDatabase()
            session.refetchUsers()
            val slackUser = session.findUserById(presenceChange.userId)
            if (!slackUser.isBot) {
                val slackMessage = SlackDetails()
                slackMessage.userID = slackUser.id
                slackMessage.userTimezoneOffset = convertTimeOff(slackUser.timeZoneOffset)
                slackMessage.userTimezoneOffsetMilisec = slackUser.timeZoneOffset
                slackMessage.userTimezoneLabel = slackUser.timeZoneLabel
                if (!databaseSlackUsers.contains(slackMessage.userID)) {
                    val userEmail = slackUser.userMail
                    val userNickName = slackUser.userName
                    databaseManager.findUserID(userEmail, slackMessage.userID, userNickName)
                    databaseSlackUsers.add(slackMessage.userID)
                }
                slackMessage.email = slackUser.userMail
                slackMessage.status = presenceChange.presence.toString()
                databaseManager.saveStatusInDatabase(slackMessage)
            }
        }
        session.addPresenceChangeListener(slackUserChangeListener)
    }

    /**
     * Used for purpose registering a listener which will be called when
     * bot is added to a channel that it hasn't seen before.

     * @param session from Slack
     */
    fun registeringChannelCreatedListener(session: SlackSession) {


        val slackChannelJoinedListener = SlackChannelJoinedListener { slackChannelJoined, slackSession ->
            val slackMessage = SlackDetails()
            slackMessage.slackSideID = slackSession.team.id
            slackMessage.channelID = slackChannelJoined.slackChannel.id
            slackMessage.channelName = slackChannelJoined.slackChannel.name

            DatabaseManager(keys).newChannelCreated(slackMessage)

        }
        session.addChannelJoinedListener(slackChannelJoinedListener)
    }


    fun userTyping(session: SlackSession) {

        val slackUserTyping = UserTypingListener { userTyping, _ ->
            val slackMessage = SlackDetails()
            slackMessage.userID = userTyping.user.id
            slackMessage.channelID = userTyping.channel.id
            slackMessage.userTimezoneOffsetMilisec = userTyping.user.timeZoneOffset
            slackMessage.userTimezoneOffset = convertTimeOff(userTyping.user.timeZoneOffset)
            slackMessage.userTimezoneLabel = userTyping.user.timeZoneLabel

            DatabaseManager(keys).userTypingEvent(slackMessage)

        }
        session.addUserTypingListener(slackUserTyping)
    }

    fun reaction(session: SlackSession) {
        val reaction = ReactionAddedListener { reactionMessage, _ ->
            val slackMessage = SlackDetails()
            slackMessage.userID = reactionMessage.user.id
            slackMessage.emojiName = reactionMessage.emojiName
            slackMessage.userTimezoneOffsetMilisec = reactionMessage.user.timeZoneOffset
            slackMessage.userTimezoneLabel = reactionMessage.user.timeZoneLabel
            slackMessage.userTimezoneOffset = convertTimeOff(reactionMessage.user.timeZoneOffset)
            slackMessage.slackMessageID = reactionMessage.messageID

            DatabaseManager(keys).messageReaction(slackMessage)
        }
        session.addReactionAddedListener(reaction)
    }

    fun convertTimeOff(timeOffSet: Int): Double {
        return timeOffSet.toDouble() / 3600
    }

}


