import com.ullink.slack.simpleslackapi.SlackTeam
import com.ullink.slack.simpleslackapi.SlackUser
import objects.SlackDetails
import objects.SurveyQuestions
import objects.UserActivity
import java.sql.*
import java.util.*
import java.sql.Timestamp
import java.util.Date
import kotlin.collections.ArrayList
import java.lang.reflect.Array.setInt


/**
 * Created by Mateusz on 22/05/2017.
 */

class DatabaseManager(private var keys: Keys) {
    private var mConnection: Connection? = null
    private var DB_URL: String = keys.databaseURL
    private var DATABASEUSER: String = keys.databaseUser
    private var PASS: String = keys.databasePass
    private val ACTIVITYTRACKER = "activityTracker"
    private val SLACKUSERID = "slackUserId"
    private val WELCOMEDMCHECK = "welcomeDM"
    private val TIMEZONE = "timezoneSetUp"
    private val SURVEYDUMMYCHECK = "surveyDummy"
    private val SURVEYMBTICHECK = "surveyMBTI"
    private val SURVEYPAEICHECK = "surveyPAEI"
    private val SURVEYVARKCHECK = "surveyVARK"
    private val SURVEYNAME = "surveyName"
    private val QUESTIONORDER = "questionOrder"
    private val QUESTIONTEXT = "questionText"
    private val LASTANSWEREDQUESTION = "lastAnsweredQuestion"
    private val SURVEYQUESTION = "surveyQuestion"
    private val SURVEYQUESTIONID = "surveyQuestionId"
    private val OPTIONTEXT = "optionText"
    private val OPTIONORDER = "optionOrder"
    private val SURVEYOPTION = "surveyOption"
    private val WELCOMEDM = "welcomeDM"
    private val AMOUNTOFREMINDERSSENT = "amountOfRemindersSent"
    private val MBTI = "surveyMbti"
    private val PAEI = "surveyPaei"
    private val VARK = "surveyVark"
    private val SUBMISSIONTIME = "submissionTime"
    private val USERID = "userID"
    private val USER = "user"
    private val SLACKUSER = "slackUser"
    private val SLACKCHAT = "slackChat"
    private val SLACKCHATID = "slackChatId"
    private val SLACKSITEID = "slackSiteId"
    private val PRODUCER = "producer"
    private val ADMINISTRATOR = "administrator"
    private val ENTREPRENEUR = "entrepreneur"
    private val INTEGRATOR = "integrator"

    private fun getConnection(): Connection {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver")
            //if (mConnection == null || !mConnection!!.isValid(1)) {
            mConnection = DriverManager.getConnection(DB_URL, DATABASEUSER, PASS)
            //}
        } catch (e: Exception) {
            System.err.println(e.message)
            mConnection = null
        }
        return mConnection!!
    }

    /**
     * Used for purpose of saving users message in a database,
     * it also checks if there is a user mentioned in a message and calls savMentionedInDatabase method
     * @param message contains data used for purpose of saving a message
     */
    fun saveMessageInDatabase(message: SlackDetails) {
        /**
         * isMessage checks if the message with this slackID already exists.
         * This is happening when one of the thread messages is deleted, and slack resend the entire thread to chat.
         * The header message would be added to the database again, if it wasn't for this if statement
         */
        val isMessage: Int = checkIfSlackMessageIdExists(message)
        if (isMessage == 0) {
            writeMessageToDB(message)
        } else {
            val isMessageSame: Boolean = checkIfSlackMessageIsSame(message)
            if (!isMessageSame) {
                if (message.message != "This message was deleted.") {
                    writeMessageToDB(message)
                } else {
                    messageDeleted(message)
                }

            }
        }
    }

    /***
     * Method used for purpose of inserting a message from slack to a database
     * @param message an object that contains all the data that should be inserted into a database
     */
    private fun writeMessageToDB(message: SlackDetails) {
        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->

                val query = "INSERT INTO chatMessage (slackChatId,writerSlackUserId,content,userLocalTime,slackMessageId,threadId,userTimezoneOffset,userTimezoneLabel) " + "VALUES (?,?,?,DATE_ADD(UTC_TIMESTAMP(), INTERVAL ${message.userTimezoneOffsetMilisec} SECOND),?,?,?,?)"
                val preparedStatement = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
                preparedStatement.setString(1, message.channelID)
                preparedStatement.setString(2, message.userID)
                preparedStatement.setString(3, message.message)
                preparedStatement.setString(4, message.slackMessageID)
                preparedStatement.setString(5, message.threadID)
                preparedStatement.setDouble(6, message.userTimezoneOffset)
                preparedStatement.setString(7, message.userTimezoneLabel)

                preparedStatement.executeUpdate()

                val rs = preparedStatement.generatedKeys
                rs.next()
                if (message.replaces.size > 0) {
                    val rowID = rs.getInt(1)
                    saveMentionedInDatabase(rowID, message.replaces, conn)
                }
                rs.close()
                preparedStatement.close()
                stmt.close()
            }
            conn.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    /***
     * Method used to detect if the message was deleted
     */
    private fun checkIfSlackMessageIsSame(message: SlackDetails): Boolean {

        var dbMessage = ""
        try {

            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT * FROM `chatMessage` WHERE slackMessageId = \"${message.slackMessageID}\" ORDER BY chatMessageId DESC LIMIT 1;"
                val rs = stmt.executeQuery(query)
                rs.next()
                dbMessage = rs.getString("content")

                rs.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return dbMessage == message.message
    }

    /***
     * Method used to check if the message ID is already in database to decide if message was deleted
     */
    private fun checkIfSlackMessageIdExists(message: SlackDetails): Int {
        var count = 0
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT count(1) FROM `chatMessage` WHERE slackMessageId = \"${message.slackMessageID}\";"
                val rs = stmt.executeQuery(query)
                rs.next()
                count = rs.getString(1).toInt()
                rs.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return count
    }

    /**
     * Used for purpose of saving mentioned users in a message in a database
     * @param rowID rowID used a primaryKey for the table
     * *
     * @param replaces list of users mentioned in a message
     * *
     * @param conn database connection object
     * *
     * @throws SQLException
     */
    @Throws(SQLException::class)
    private fun saveMentionedInDatabase(rowID: Int, replaces: ArrayList<String>, conn: Connection) {
        for (userName in replaces) {
            val query = "INSERT INTO messageMention (chatMessageId, mentionedUserId) " + "VALUES (?,?)"
            val preparedStatement = conn.prepareStatement(query)
            preparedStatement.setInt(1, rowID)
            preparedStatement.setString(2, userName)
            preparedStatement.executeUpdate()
            preparedStatement.close()

        }
        conn.close()
    }

    /**
     * used for purpose of saving user's status in a database
     * @param message object containing all the required variables for saving user's status in a table
     */
    @Throws(SQLException::class)
    fun saveStatusInDatabase(message: SlackDetails) {
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                //                val query = "INSERT INTO slackUserStatusLog (slackUserId,email,status,userLocalTime,userTimezoneOffset,userTimezoneLabel) " + "VALUES (?,?,?,DATE_ADD(NOW(), INTERVAL ${message.userTimezoneOffsetMilisec} SECOND),?,?)"
                val query = "INSERT INTO slackUserStatusLog ($SLACKUSERID,email,status,userLocalTime,userTimezoneOffset,userTimezoneLabel) " + "VALUES (?,?,?,DATE_ADD(UTC_TIMESTAMP(), INTERVAL ${message.userTimezoneOffsetMilisec} SECOND),?,?)"
                val preparedStatement = conn.prepareStatement(query)

                preparedStatement.setString(1, message.userID)
                preparedStatement.setString(2, message.email)
                preparedStatement.setString(3, message.status)
                preparedStatement.setDouble(4, message.userTimezoneOffset)
                preparedStatement.setString(5, message.userTimezoneLabel)
                preparedStatement.executeUpdate()
                preparedStatement.close()
                stmt.close()
                conn.close()
            }

        } catch (e: SQLException) {
            e.printStackTrace()
        }

    }

    /**
     * Method used for purpose of adding newly created channel into a database
     * @param message containing all the required variables for purpose of saving new channel to the database
     */
    fun newChannelCreated(message: SlackDetails) {
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                var query = "SELECT * FROM slackChat WHERE slackChatId = \"${message.channelID}\";"
                val rs = stmt.executeQuery(query)

                if (!rs.isBeforeFirst) {
                    rs.close()

                    query = "INSERT INTO slackChat (slackChatId,slackSiteId,name) " + "VALUES (?,?,?)"
                    val preparedStatement = conn.prepareStatement(query)
                    preparedStatement.setString(1, message.channelID)
                    preparedStatement.setString(2, message.slackSideID)
                    preparedStatement.setString(3, message.channelName)
                    preparedStatement.executeUpdate()
                    preparedStatement.close()
                    stmt.close()
                    conn.close()
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    /***
     * Method used for purpose of tracking user survey activity
     * @param slackDetails contains user details
     */
    fun checkUserActivityStage(slackDetails: SlackDetails): UserActivity {
        val userActivity = UserActivity()
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->

                val query = "SELECT * FROM $ACTIVITYTRACKER WHERE $SLACKUSERID = \"${slackDetails.userID}\""
                val rs = stmt.executeQuery(query)
                while (rs.next()) {
                    userActivity.userID = rs.getString(SLACKUSERID)
                    userActivity.welcomeMessage = rs.getInt(WELCOMEDMCHECK)
                    userActivity.timezoneCheck = rs.getInt(TIMEZONE)
                    userActivity.surveyDummy = rs.getInt(SURVEYDUMMYCHECK)
                    userActivity.surveyMBTI = rs.getInt(SURVEYMBTICHECK)
                    userActivity.surveyPAEI = rs.getInt(SURVEYPAEICHECK)
                    userActivity.surveyVARK = rs.getInt(SURVEYVARKCHECK)
                }
                stmt.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return userActivity
    }

    /***
     * Method used for purpose of inserting slack chat id and slack site id into database
     */
    private fun insertChatIdToChatTable(slackDetails: SlackDetails) {
        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->

                val query = "INSERT INTO $SLACKCHAT ($SLACKCHATID,$SLACKSITEID) VALUES (?,?)"
                val preparedStatement = conn.prepareStatement(query)
                preparedStatement.setString(1, slackDetails.channelID)
                preparedStatement.setString(2, slackDetails.teamID)

                preparedStatement.executeUpdate()
                preparedStatement.close()
                stmt.close()
            }
            conn.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    /***
     * Method used for purpose of selecting all the users from a database
     */
    fun selectAllSlackUsersFromDatabase(): ArrayList<String> {
        val slackMessages = ArrayList<String>()
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT * FROM slackUser"
                val rs = stmt.executeQuery(query)
                while (rs.next()) {
                    val slackUserID = rs.getString("slackUserId")
                    slackMessages.add(slackUserID)
                }
                stmt.close()
                conn.close()
            }


        } catch (e: SQLException) {
            e.printStackTrace()
        }

        return slackMessages
    }

    /***
     * Method used for purpose of inserting user to slack user table
     */
    fun insertUserToSlackUserTable(email: String, slackID: String, slackNickName: String) {
        var userID: String? = null
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                var query = "SELECT * FROM `user` WHERE `email` =" + "\"" + email + "\""
                val rs = stmt.executeQuery(query)
                while (rs.next()) {
                    userID = rs.getString("userId")
                }
                rs.close()
                stmt.close()
                query = "INSERT INTO slackUser (slackUserId,userId,userAlias) " + "VALUES (?,?,?)"
                val preparedStatement = conn.prepareStatement(query)
                preparedStatement.setString(1, slackID)
                preparedStatement.setString(2, userID)
                preparedStatement.setString(3, slackNickName)
                preparedStatement.executeUpdate()
                preparedStatement.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }

    }

    /***
     * Method used for purpose of retriving user group
     */
    fun getUserGroup(): ArrayList<SlackDetails> {
        val slackMessageArrayList = ArrayList<SlackDetails>()
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT " +
                        "su.slackUserId," +
                        "sc.slackChatId," +
                        "g.name as 'group name'," +
                        "p.projectId FROM `user` AS u " +
                        "INNER JOIN `slackUser` AS su On `su`.`userId` = `u`.`userId`" +
                        "INNER JOIN `userGroup` AS ug ON `ug`.`userId` = `u`.`userId`" +
                        "INNER JOIN `group` AS g ON `g`.`groupId` = `ug`.`groupId`" +
                        "INNER JOIN `slackChat` AS sc ON `sc`.`groupId` = `g`.`groupId`" +
                        "INNER JOIN `project` AS p ON `p`.`projectId` = `g`.`projectId`" +
                        "WHERE p.projectId = '${keys.projectName}'"
                val rs = stmt.executeQuery(query)
                while (rs.next()) {
                    val slackMessage = SlackDetails()
                    slackMessage.userID = rs.getString("slackUserId")
                    slackMessage.channelID = rs.getString("slackChatId")
                    slackMessageArrayList.add(slackMessage)
                }
                rs.close()
                stmt.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return slackMessageArrayList
    }

    /***
     * Method used for purpose of retriving users that should be invited into Slack
     */
    fun getUsersToInvite(): ArrayList<SlackDetails> {
        val slackMessageArrayList = ArrayList<SlackDetails>()
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val projectName = keys.projectName
                val query = "SELECT `user`.`userId`,`user`.`firstName`,`user`.`email`,`project`.`projectId`" +
                        "FROM `user`" +
                        "INNER JOIN `userGroup` ON `user`.`userId` = `userGroup`.`userId`" +
                        "INNER JOIN `group` ON `userGroup`.`groupId` = `group`.`groupId`" +
                        "INNER JOIN `project` ON `group`.`projectId` = `project`.`projectId`" +
                        "WHERE project.projectId = \"$projectName\";"
                val rs = stmt.executeQuery(query)
                while (rs.next()) {
                    val slackMessage = SlackDetails()
                    slackMessage.email = rs.getString("email")
                    slackMessage.firstName = rs.getString("firstName")
                    slackMessageArrayList.add(slackMessage)
                }
                rs.close()
                stmt.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return slackMessageArrayList
    }

    /***
     * Method used for checking if the team is in database
     */
    fun isTeamInDatabase(slackTeam: SlackTeam): Boolean {
        var isTeaminDatabase = false
        val conn = getConnection()
        try {
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val teamID = slackTeam.id
                val query = "  SELECT `slackSiteId` FROM `slackSite`WHERE slackSiteId =\"$teamID\";"
                val rs = stmt.executeQuery(query)
                if (rs.next()) {
                    isTeaminDatabase = true
                }
                rs.close()
                stmt.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }

        return isTeaminDatabase

    }

    /***
     * Method used for purpose of adding team to the database
     */
    fun addTeamToDatabase(slackTeam: SlackTeam) {

        try {
            val conn = getConnection()
            val projectName = keys.projectName
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->

                val query = "INSERT INTO slackSite ( slackSiteId,projectId,name,url,description) " + "VALUES (?,?,?,?,?)"
                val preparedStatement = conn.prepareStatement(query)
                preparedStatement.setString(1, slackTeam.id)
                preparedStatement.setString(2, projectName)
                preparedStatement.setString(3, projectName)
                preparedStatement.setString(4, slackTeam.domain + "slack.com")
                preparedStatement.setString(5, projectName)
                preparedStatement.executeUpdate()

                preparedStatement.close()
                stmt.close()
            }
            conn.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    /***
     * Method used for purpose of retrieving bot details from a database
     */
    fun getBotDetails(): Keys {

        val conn = getConnection()
        try {
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->

                val query = "  SELECT * FROM `botDetails` WHERE portNumber =\"${keys.portNumber}\";"
                val rs = stmt.executeQuery(query)
                if (rs.next()) {
                    keys.slackKey = rs.getString("slackKey")
                    keys.slackAdminKey = rs.getString("slackAdminKey")
                    keys.projectName = rs.getString("projectName")
                }
                rs.close()
                stmt.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }

        return keys
    }

    /**
     *
     * used for purpose of saving event of user typing
     * @param message object containing all the required variables for saving user typing event
     */
    @Throws(SQLException::class)
    fun userTypingEvent(message: SlackDetails) {
        val count = checkIfSlackChannelIdExists(message)

        if (count == 0) {
            insertChatIdToChatTable(message)
        }
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "INSERT INTO userTyping (" +
                        "slackChatId," +
                        "writerSlackUserId," +
                        "userLocalTime," +
                        "userTimezoneOffset," +
                        "userTimezoneLabel) " +
                        "VALUES (?,?,DATE_ADD(UTC_TIMESTAMP(), INTERVAL ${message.userTimezoneOffsetMilisec} SECOND),?,?)"
                val preparedStatement = conn.prepareStatement(query)

                preparedStatement.setString(1, message.channelID)
                preparedStatement.setString(2, message.userID)
                preparedStatement.setDouble(3, message.userTimezoneOffset)
                preparedStatement.setString(4, message.userTimezoneLabel)

                preparedStatement.executeUpdate()
                preparedStatement.close()
                stmt.close()
                conn.close()
            }

        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    /***
     * Method used for purpose of checking if channel is already in a database
     */
    private fun checkIfSlackChannelIdExists(message: SlackDetails): Int {
        var count = 0
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT count(1) FROM `$SLACKCHAT` WHERE $SLACKCHATID = \"${message.channelID}\";"
                val rs = stmt.executeQuery(query)
                rs.next()
                count = rs.getString(1).toInt()
                rs.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return count
    }

    /***
     * Method used for purpose of inserting message reactions into table
     */
    fun messageReaction(message: SlackDetails) {
        val conn = getConnection()
        var dbMessageID: String? = null
        try {
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                var query = "  SELECT `chatMessageId` FROM `chatMessage`WHERE slackMessageId =\"${message.slackMessageID}\";"
                val rs = stmt.executeQuery(query)
                if (rs.next()) {
                    dbMessageID = rs.getString("chatMessageId")
                }
                rs.close()
                stmt.close()
                query = "INSERT INTO messageReaction (channelMessageId," +
                        "userReactingId," +
                        "emojiName," +
                        "userLocalTime," +
                        "userTimezoneOffset," +
                        "userTimezoneLabel) " +
                        "VALUES (?,?,?,DATE_ADD(UTC_TIMESTAMP(), INTERVAL ${message.userTimezoneOffsetMilisec} SECOND),?,?)"
                val preparedStatement = conn.prepareStatement(query)
                preparedStatement.setString(1, dbMessageID)
                preparedStatement.setString(2, message.userID)
                preparedStatement.setString(3, message.emojiName)
                preparedStatement.setDouble(4, message.userTimezoneOffset)
                preparedStatement.setString(5, message.userTimezoneLabel)
                preparedStatement.executeUpdate()
                preparedStatement.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    /***
     * Method used for purpose of inserting edited message into a database
     * @param message contains message details
     */
    fun messageEdited(message: SlackDetails) {
        val conn = getConnection()
        try {
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "  SELECT * FROM `chatMessage`WHERE slackMessageId =\"${message.slackMessageID}\";"
                val rs = stmt.executeQuery(query)
                if (rs.next()) {
                    message.userID = rs.getString("writerSlackUserId")
                    message.userTimezoneOffset = rs.getDouble("userTimezoneOffset")
                    message.userTimezoneLabel = rs.getString("userTimezoneLabel")
                    message.userTimezoneOffsetMilisec = (message.userTimezoneOffset * 3600).toInt()
                    message.threadID = rs.getString("threadId")
                }
                rs.close()
                stmt.close()
                conn.close()
            }
            saveMessageInDatabase(message)

        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    /***
     * Method used for purpose of checking if the message was deleted
     */

    private fun isMessageDeleted(message: SlackDetails): Boolean {
        var isDeleted = true
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT * FROM `chatMessage` WHERE slackMessageId = \"${message.slackMessageID}\";"
                val rs = stmt.executeQuery(query)
                rs.next()
                if (rs.getTimestamp("deletedLocaltime") == null) {
                    isDeleted = false
                }
                rs.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return isDeleted
    }

    /***
     * Method used for purpose of marking a message as deleted
     */
    fun messageDeleted(message: SlackDetails) {
        if (!isMessageDeleted(message)) {
            val conn = getConnection()
            try {

                val ps = conn.prepareStatement(
                        "UPDATE chatMessage " +
                                "AS t1 INNER JOIN (SELECT userTimezoneOffset FROM chatMessage WHERE slackMessageId = ? LIMIT 1)" +
                                "AS t2 SET t1.deletedLocaltime = DATE_ADD(UTC_TIMESTAMP(), INTERVAL (3600 * t2.userTimezoneOffset) SECOND)" +
                                " WHERE slackMessageId = ?;")
                ps.setString(1, message.slackMessageID)
                ps.setString(2, message.slackMessageID)
                ps.executeUpdate()
                ps.close()


            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    /***
     * Method used by the bot to send a heartbeat to a database
     */
    fun hearthBeat(projectName: String, heartBeat: Boolean) {
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val date = Date()
                val timestamp = Timestamp(date.time)
                val query = "UPDATE botDetails SET heartBeat = ?, heartBeatTime = ? WHERE projectName = ?"
                val preparedStatement = conn.prepareStatement(query)
                preparedStatement.setBoolean(1, heartBeat)
                preparedStatement.setTimestamp(2, timestamp, Calendar.getInstance(TimeZone.getTimeZone("UTC")))
                preparedStatement.setString(3, projectName)
                preparedStatement.executeUpdate()

                preparedStatement.close()
                stmt.close()
            }
            conn.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    /***
     * Method used for purpose of reading a welcome message
     * @param slackChannel a name of channel to select correct welcome message
     */
    fun readWelcomeMessage(slackChannel: String): String {

        var welcomeMessage = ""

        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT `welcomeMessage` FROM `slackChat` where slackChatId = \"$slackChannel \""
                val rs = stmt.executeQuery(query)
                if (rs.next()) {
                    welcomeMessage = rs.getString("welcomeMessage")
                }
                stmt.close()
                conn.close()
            }


        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return welcomeMessage
    }

    /***
     * Method used for purpose of setting up activity tracker table for users
     * @param slackUser contains details of the user
     */
    fun setupActivityTracker(slackUser: SlackUser) {
        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->

                val query = "INSERT INTO $ACTIVITYTRACKER ($SLACKUSERID,$WELCOMEDM,$LASTANSWEREDQUESTION,$AMOUNTOFREMINDERSSENT) VALUES (?,?,?,?)"
                val preparedStatement = conn.prepareStatement(query)
                preparedStatement.setString(1, slackUser.id)
                preparedStatement.setInt(2, 1)
                preparedStatement.setInt(3, 0)
                preparedStatement.setInt(4, 2)

                preparedStatement.executeUpdate()
                preparedStatement.close()
                stmt.close()
            }
            conn.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    /***
     * Method used for purpose of updating activity tracker
     */
    fun updateActivityTracker(columnToUpdate: Int, userID: String) {
        var columnName = ""
        when (columnToUpdate) {
            1 -> columnName = "timezoneSetUp"
            2 -> columnName = "surveyDummy"
            3 -> columnName = "surveyMBTI"
            4 -> columnName = "surveyPAEI"
            5 -> columnName = "surveyVARK"
        }

        val conn = getConnection()
        try {
            val ps = conn.prepareStatement(
                    "UPDATE $ACTIVITYTRACKER SET $columnName=1 , $LASTANSWEREDQUESTION = 0 WHERE $SLACKUSERID = ?;")
            ps.setString(1, userID)
            ps.executeUpdate()
            ps.close()


        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    /***
     * Method used to retrieve survey question from a database
     * @param surveyName name of the survey
     * @param userID user ID
     */
    fun getSurveyQuestion(surveyName: String, userID: String): SurveyQuestions {
        val surveyQuestions = SurveyQuestions()
        var lastAnswered = 0
        var questionText = ""
        var questionID: Int
        val optionArrayList: ArrayList<String> = ArrayList()

        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->

                //collects last question that user answered
                val query = "SELECT $LASTANSWEREDQUESTION FROM $ACTIVITYTRACKER WHERE $SLACKUSERID = \"$userID\""
                val rs = stmt.executeQuery(query)
                rs.last()
                lastAnswered = rs.getInt(LASTANSWEREDQUESTION) + 1
                //collects question text and id
                val query1 = "SELECT $QUESTIONTEXT,$SURVEYQUESTIONID FROM $SURVEYQUESTION WHERE " +
                        "$QUESTIONORDER = \"$lastAnswered\" AND " +
                        "$SURVEYNAME = \"$surveyName\""
                val rs1 = stmt.executeQuery(query1)
                rs1.last()
                questionText = rs1.getString(QUESTIONTEXT)
                questionID = rs1.getInt(SURVEYQUESTIONID)
                //collects answer text and order of answers
                val query2 = "SELECT $OPTIONTEXT,$OPTIONORDER FROM $SURVEYOPTION WHERE " +
                        "$SURVEYQUESTIONID = \"$questionID\" ORDER BY $OPTIONORDER ASC"
                val rs2 = stmt.executeQuery(query2)
                while (rs2.next()) {
                    val text = rs2.getString(OPTIONTEXT)
                    optionArrayList.add(text)
                }

                stmt.close()
                conn.close()
            }


        } catch (e: SQLException) {
            e.printStackTrace()
        }
        surveyQuestions.questionNumber = lastAnswered
        surveyQuestions.questionText = questionText
        surveyQuestions.questionAnswers = optionArrayList
        return surveyQuestions
    }

    /***
     * Method used for purpose of selecting question number that the user should answer
     * The database will return +1 value which is the current number of question
     * The survey questions are 1 indexed
     * the values in database are stored 0 indexed
     */
    fun selectCurrentUserQuestion(userID: String): Int {
        var lastAnswered = 0
        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT $LASTANSWEREDQUESTION FROM $ACTIVITYTRACKER WHERE $SLACKUSERID = \"$userID\""
                val rs = stmt.executeQuery(query)
                rs.last()
                lastAnswered = rs.getInt(LASTANSWEREDQUESTION) + 1

                stmt.close()
                conn.close()
            }


        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return lastAnswered
    }

    /***
     * Method used for purpose of retrieving a question ID
     */
    private fun getSurveyQuestionID(surveyName: String, questionNumber: Int): Int {

        var surveyQuestionID = 0
        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT $SURVEYQUESTIONID FROM $SURVEYQUESTION WHERE $SURVEYNAME = \"$surveyName\" AND $QUESTIONORDER = \"$questionNumber\" "
                val rs = stmt.executeQuery(query)
                rs.last()
                surveyQuestionID = rs.getInt(SURVEYQUESTIONID)
                stmt.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return surveyQuestionID
    }

    /***
     * Method used for purpose of selecting number of questions for survey
     */
    private fun selectNumberOfQuestionForSurvey(surveyName: String): Int {

        var totalNumberOfQuestions = 0
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT count($SURVEYNAME) from $SURVEYQUESTION where $SURVEYNAME = \"$surveyName\";"
                val rs = stmt.executeQuery(query)
                rs.last()
                totalNumberOfQuestions = rs.getInt(1)
                rs.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return totalNumberOfQuestions
    }

    fun totalNumberOfOptions(surveyName: String, userID: String): ArrayList<Int> {

        val currentQuestion = selectCurrentUserQuestion(userID)
        val currentSurvey = getSurveyQuestionID(surveyName, currentQuestion)
        val totalNumberOfQuestions = selectNumberOfQuestionForSurvey(surveyName)
        val intArrayList: ArrayList<Int> = ArrayList()

        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT count(1) FROM `surveyOption` WHERE $SURVEYQUESTIONID = \"$currentSurvey\";"
                val rs = stmt.executeQuery(query)
                rs.last()
                intArrayList.add(rs.getInt(1))

                rs.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        intArrayList.add(currentQuestion)
        intArrayList.add(totalNumberOfQuestions)
        return intArrayList
    }

    fun updateLastAnsweredQuestionColumn(userID: String) {
        val conn = getConnection()
        try {
            val ps = conn.prepareStatement(
                    "UPDATE $ACTIVITYTRACKER SET $LASTANSWEREDQUESTION=$LASTANSWEREDQUESTION+1 WHERE $SLACKUSERID = ?;")
            ps.setString(1, userID)
            ps.executeUpdate()
            ps.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun selectNumberOfRemindersAvailable(userID: String): Int {
        var remindersAvailable = 0
        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT $AMOUNTOFREMINDERSSENT FROM $ACTIVITYTRACKER WHERE $SLACKUSERID = \"$userID\""
                val rs = stmt.executeQuery(query)
                rs.last()
                remindersAvailable = rs.getInt(AMOUNTOFREMINDERSSENT)
                stmt.close()
                conn.close()
            }


        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return remindersAvailable
    }

    fun reduceNumberOfReminders(userID: String) {
        val conn = getConnection()
        try {
            val ps = conn.prepareStatement(
                    "UPDATE $ACTIVITYTRACKER SET $AMOUNTOFREMINDERSSENT=$AMOUNTOFREMINDERSSENT-1 WHERE $SLACKUSERID = ?;")
            ps.setString(1, userID)
            ps.executeUpdate()
            ps.close()


        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun resetNumberOfReminders(userID: String) {
        val conn = getConnection()
        try {
            val ps = conn.prepareStatement(
                    "UPDATE $ACTIVITYTRACKER SET $AMOUNTOFREMINDERSSENT=2 WHERE $SLACKUSERID = ?;")
            ps.setString(1, userID)
            ps.executeUpdate()
            ps.close()


        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun createSurveyRow(slackUser: SlackUser, surveyName: String) {
        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                var query = ""


                when (surveyName) {
                    "MBTI" -> query = "INSERT INTO $MBTI ($USERID) VALUES (?)"
                    "PAEI" -> query = "INSERT INTO $PAEI ($USERID) VALUES (?)"
                    "vark" -> query = "INSERT INTO $VARK ($USERID) VALUES (?)"
                }
                val userID = selectUserIDBasedOnSlackID(slackUser.id)
                val preparedStatement = conn.prepareStatement(query)
                preparedStatement.setString(1, userID)

                preparedStatement.executeUpdate()
                preparedStatement.close()
                stmt.close()
            }
            conn.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun setAnswer(slackUserID: String, surveyName: String, questionNumber: Int, userAnswer: Int) {

        val question = "q$questionNumber"
        val conn = getConnection()
        try {
            val userID = selectUserIDBasedOnSlackID(slackUserID)
            var surveyNameTable = String()
            when (surveyName) {
                "mbti" -> surveyNameTable = "surveyMbti"
                "paei" -> surveyNameTable = "surveyPaei"
            }
            val ps = conn.prepareStatement(
                    "UPDATE $surveyNameTable SET $question=?, $SUBMISSIONTIME = UTC_TIMESTAMP() WHERE $USERID = ?;")
            ps.setInt(1, userAnswer)
            ps.setString(2, userID)
            ps.executeUpdate()
            ps.close()


        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun decreaseLastAnsweredQuestionColumn(userID: String) {
        val conn = getConnection()
        try {
            val ps = conn.prepareStatement(
                    "UPDATE $ACTIVITYTRACKER SET $LASTANSWEREDQUESTION=$LASTANSWEREDQUESTION-1 WHERE $SLACKUSERID = ?;")
            ps.setString(1, userID)
            ps.executeUpdate()
            ps.close()


        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    private fun selectUserIDBasedOnSlackID(slackUserId: String): String {

        var userID = ""
        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT $USERID FROM $SLACKUSER WHERE $SLACKUSERID = \"$slackUserId\""
                val rs = stmt.executeQuery(query)
                rs.last()
                userID = rs.getString(USERID)
                stmt.close()
                conn.close()
            }


        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return userID
    }

    fun selectAllTheAnswersBasedOnUserID(surveyName: String, slackUserId: String): ArrayList<Int> {

        val userID = selectUserIDBasedOnSlackID(slackUserId)
        val results = ArrayList<Int>()
        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                var query = ""
                var questionNumber = 0
                when (surveyName) {
                    "paei" -> {
                        query = "SELECT q1,q2,q3,q4,q5,q6,q7,q8,q9,q10,q11,q12,q13,q14,q15,q16,q17,q18,q19,q20," +
                                "q21,q22,q23,q24,q25,q26,q27 FROM $PAEI WHERE $USERID = \"$userID\""
                        questionNumber = 27
                    }
                    "mbti" -> {
                        query = "SELECT q1,q2,q3,q4,q5,q6,q7,q8,q9,q10,q11,q12,q13,q14,q15,q16,q17,q18,q19,q20,q21," +
                                "q22,q23,q24,q25,q26,q27,q28,q29,q30,q31,q32,q33,q34,q35,q36,q37,q38,q39,q40,q41,q42,q43,q44," +
                                "q45,q46,q47,q48,q49,q50,q51,q52,q53,q54,q55,q56,q57,q58,q59,q60,q61,q62,q63,q64,q65,q66,q67," +
                                "q68,q69,q70 FROM $MBTI WHERE $USERID = \"$userID\""
                        questionNumber = 70
                    }
                    "vark" -> {
                        query = "SELECT q1,q2,q3,q4,q5,q6,q7,q8,q9,q10,q11,q12,q13,q14,q15,q16 FROM $VARK WHERE $USERID = \"$userID\""
                        questionNumber = 16
                    }
                }
                val rs = stmt.executeQuery(query)
                rs.last()
                (1..questionNumber).mapTo(results) { rs.getInt("q$it") }
                stmt.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return results
    }

    fun getAllVarkAnswers(answers: ArrayList<Int>): HashMap<String, Int> {
        val result: HashMap<String, Int> = hashMapOf("v" to 0, "a" to 0, "r" to 0, "k" to 0)
        var v = 0
        var a = 0
        var r = 0
        var k = 0
        val conn = getConnection()
        answers.forEach { answerID ->
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT v, a, r,k FROM surveyVarkChosenOptions WHERE surveyVarkChosenOptionsId = $answerID"
                println(query)
                val rs = stmt.executeQuery(query)
                rs.last()
                v += rs.getInt(1)
                a += rs.getInt(2)
                r += rs.getInt(3)
                k += rs.getInt(4)
                stmt.close()
            }
        }
        conn.close()
        result["v"] = v
        result["a"] = a
        result["r"] = r
        result["k"] = k
        return result
    }

    fun setTally(slackUserID: String, surveyName: String, tally: HashMap<String, Int>) {

        val conn = getConnection()
        try {
            val userID = selectUserIDBasedOnSlackID(slackUserID)
            val ps: PreparedStatement
            when (surveyName) {
                "mbti" -> {
                    ps = conn.prepareStatement(
                            "UPDATE surveyMbti SET e=?,i=?,s=?,n=?,t=?,f=?,j=?,p=? WHERE $USERID = ?;")
                    ps.setInt(1, tally["e"] as Int)
                    ps.setInt(2, tally["i"] as Int)
                    ps.setInt(3, tally["s"] as Int)
                    ps.setInt(4, tally["n"] as Int)
                    ps.setInt(5, tally["t"] as Int)
                    ps.setInt(6, tally["f"] as Int)
                    ps.setInt(7, tally["j"] as Int)
                    ps.setInt(8, tally["p"] as Int)
                    ps.setString(9, userID)
                    ps.executeUpdate()
                    ps.close()
                }
                "paei" -> {
                    ps = conn.prepareStatement(
                            "UPDATE surveyPaei SET $PRODUCER = ?, $ADMINISTRATOR = ?, $ENTREPRENEUR = ?, $INTEGRATOR = ? WHERE $USERID = ?;")
                    ps.setInt(1, tally["p"] as Int)
                    ps.setInt(2, tally["a"] as Int)
                    ps.setInt(3, tally["e"] as Int)
                    ps.setInt(4, tally["i"] as Int)
                    ps.setString(5, userID)
                    ps.executeUpdate()
                    ps.close()
                }
                "vark" -> {
                    ps = conn.prepareStatement(
                            "UPDATE surveyVark SET visual = ?, aural = ?, kinaesthetic = ?, readwrite = ? WHERE $USERID = ?;")
                    ps.setInt(1, tally["v"] as Int)
                    ps.setInt(2, tally["a"] as Int)
                    ps.setInt(3, tally["r"] as Int)
                    ps.setInt(4, tally["k"] as Int)
                    ps.setString(5, userID)
                    ps.executeUpdate()
                    ps.close()
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun setPreviousValueNull(slackUserID: String, surveyName: String, questionNumber: Int) {

        val conn = getConnection()
        try {
            val userID = selectUserIDBasedOnSlackID(slackUserID)

            if (surveyName == "vark") {
                var questionNumberToRemove = 0
                conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                    val query = "SELECT q$questionNumber FROM surveyVark WHERE $USERID = \"$userID\";"
                    val rs = stmt.executeQuery(query)
                    rs.last()
                    questionNumberToRemove = rs.getInt("q$questionNumber")

                    stmt.close()
                }
                val SQL = "DELETE FROM surveyVarkChosenOptions WHERE surveyVarkChosenOptionsId = ?;"
                val preparedStmt = conn.prepareStatement(SQL)
                preparedStmt.setInt(1, questionNumberToRemove)
                preparedStmt.execute()
                preparedStmt.close()
            }
            val ps: PreparedStatement = conn.prepareStatement(
                    "UPDATE survey${surveyName.capitalize()} SET q$questionNumber = ? WHERE $USERID = ?;")
            ps.setNull(1, java.sql.Types.INTEGER)
            ps.setString(2, userID)
            ps.executeUpdate()
            ps.close()
            conn.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun varkAnswer(questionNumber: Int, vark: HashMap<Char?, Int>, user: SlackUser) {
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "INSERT INTO surveyVarkChosenOptions (v,a,r,k) " + "VALUES (?,?,?,?)"
                val preparedStatement = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
                vark['v']?.let { preparedStatement.setInt(1, it) }
                vark['a']?.let { preparedStatement.setInt(2, it) }
                vark['r']?.let { preparedStatement.setInt(3, it) }
                vark['k']?.let { preparedStatement.setInt(4, it) }
                preparedStatement.executeUpdate()
                val rs = preparedStatement.generatedKeys
                rs.next()
                val rowID = rs.getInt(1)
                preparedStatement.close()
                val userID = selectUserIDBasedOnSlackID(user.id)
                val ps = conn.prepareStatement(
                        "UPDATE surveyVark SET q$questionNumber = ?, submissionTime = UTC_TIMESTAMP() WHERE $USERID = ?;"
                )
                ps.setInt(1, rowID)
                ps.setString(2, userID)
                ps.executeUpdate()

                ps.close()
                rs.close()

                stmt.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

}