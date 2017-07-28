import com.ullink.slack.simpleslackapi.SlackTeam
import java.sql.*
import java.util.*
import java.sql.Timestamp
import java.util.Date


/**
 * Created by Mateusz on 22/05/2017.
 */

class DatabaseManager(var keys: Keys) {
    private var mConnection: Connection? = null
    private var DB_URL: String = keys.databaseURL
    private var USER: String = keys.databaseUser
    private var PASS: String = keys.databasePass

    private fun getConnection(): Connection {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver")
            if (mConnection == null || !mConnection!!.isValid(1)) {
                mConnection = DriverManager.getConnection(DB_URL, USER, PASS)
            }
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
        val isMessage : Int = checkIfSlackMessageIdExists(message)

        if(isMessage == 0){
            writeMessageToDB(message)
        }else{
            val isMessageSame : Boolean = checkIfSlackMessageIsSame(message)
            if(!isMessageSame){
                if(message.message!="This message was deleted."){
                    writeMessageToDB(message)
                }else{
                    messageDeleted(message)
                }

            }
        }
    }

    fun writeMessageToDB(message: SlackDetails){
        try {
            val conn = getConnection()

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->

                val query = "INSERT INTO channelMessages (slackChannelId,writerSlackUserId,content,userLocalTime,slackMessageId,threadId,userTimezoneOffset,userTimezoneLabel) " + "VALUES (?,?,?,DATE_ADD(UTC_TIMESTAMP(), INTERVAL ${message.userTimezoneOffsetMilisec} SECOND),?,?,?,?)"
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



    fun checkIfSlackMessageIsSame(message: SlackDetails): Boolean {

        var dbMessage : String = ""
        try {

            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT * FROM `channelMessages` WHERE slackMessageId = \"${message.slackMessageID}\" ORDER BY channelMessagesId DESC LIMIT 1;"
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

    fun checkIfSlackMessageIdExists(message: SlackDetails):Int{
        var count : Int = 0
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT count(1) FROM `channelMessages` WHERE slackMessageId = \"${message.slackMessageID}\";"
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
    fun saveMentionedInDatabase(rowID: Int, replaces: ArrayList<String>, conn: Connection) {
        for (userName in replaces) {
            val query1 = "INSERT INTO messageMentions (channelMessageId, mentionedUserId) " + "VALUES (?,?)"
            val preparedStatement1 = conn.prepareStatement(query1, Statement.RETURN_GENERATED_KEYS)
            preparedStatement1.setInt(1, rowID)
            preparedStatement1.setString(2, userName)
            preparedStatement1.executeUpdate()
            preparedStatement1.close()

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
                val query = "INSERT INTO slackUserStatusLog (slackUserId,email,status,userLocalTime,userTimezoneOffset,userTimezoneLabel) " + "VALUES (?,?,?,DATE_ADD(UTC_TIMESTAMP(), INTERVAL ${message.userTimezoneOffsetMilisec} SECOND),?,?)"
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
                var query = "SELECT * FROM slackChannel WHERE slackChannelId = \"${message.channelID}\";"
                val rs = stmt.executeQuery(query)

                if (!rs.isBeforeFirst) {
                    rs.close()

                    query = "INSERT INTO slackChannel (slackChannelId,slackSiteId,name) " + "VALUES (?,?,?)"
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

    fun findUserID(email: String, slackID: String, slackNickName: String) {
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

    fun getUserGroup(): ArrayList<SlackDetails> {
        val slackMessageArrayList = ArrayList<SlackDetails>()
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT " +
                        "su.slackUserId," +
                        "sc.slackChannelId," +
                        "g.name as 'group name'," +
                        "p.projectId FROM `user` AS u " +
                        "INNER JOIN `slackUser` AS su On `su`.`userId` = `u`.`userId`" +
                        "INNER JOIN `userGroup` AS ug ON `ug`.`userId` = `u`.`userId`" +
                        "INNER JOIN `group` AS g ON `g`.`groupId` = `ug`.`groupId`" +
                        "INNER JOIN `slackChannel` AS sc ON `sc`.`groupId` = `g`.`groupId`" +
                        "INNER JOIN `project` AS p ON `p`.`projectId` = `g`.`projectId`" +
                        "WHERE p.projectId = " + "\"bestFood2016\""
                val rs = stmt.executeQuery(query)
                while (rs.next()) {
                    val slackMessage = SlackDetails()
                    slackMessage.userID = rs.getString("slackUserId")
                    slackMessage.channelID = rs.getString("slackChannelId")
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
                        "WHERE project.projectId = \"${projectName}\";"
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
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "INSERT INTO userTyping (" +
                        "slackChannelId," +
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


    fun messageReaction(message: SlackDetails) {
        val conn = getConnection()
        var dbMessageID: String? = null
        try {
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                var query = "  SELECT `channelMessagesId` FROM `channelMessages`WHERE slackMessageId =\"${message.slackMessageID}\";"
                val rs = stmt.executeQuery(query)
                if (rs.next()) {
                    dbMessageID = rs.getString("channelMessagesId")
                }
                rs.close()
                stmt.close()
                query = "INSERT INTO messageReactions (channelMessageId," +
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

    fun messageEdited(message: SlackDetails) {
        val conn = getConnection()
        try {
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "  SELECT * FROM `channelMessages`WHERE slackMessageId =\"${message.slackMessageID}\";"
                val rs = stmt.executeQuery(query)
                if (rs.next()) {
                    message.userID=rs.getString("writerSlackUserId")
                    message.userTimezoneOffset = rs.getDouble("userTimezoneOffset")
                    message.userTimezoneLabel=rs.getString("userTimezoneLabel")
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

    fun isMessageDeleted(message: SlackDetails) : Boolean{
        var isDeleted : Boolean = true
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val query = "SELECT * FROM `channelMessages` WHERE slackMessageId = \"${message.slackMessageID}\";"
                val rs = stmt.executeQuery(query)
                rs.next()
                if(rs.getTimestamp("deletedLocaltime")==null){
                    isDeleted=false
                }
                rs.close()
                conn.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return isDeleted
    }

    fun messageDeleted(message: SlackDetails) {
        if(!isMessageDeleted(message)){
            val conn = getConnection()
            try {

                val ps = conn.prepareStatement(
                        "UPDATE channelMessages " +
                                "AS t1 INNER JOIN (SELECT userTimezoneOffset FROM channelMessages WHERE slackMessageId = ? LIMIT 1)" +
                                "AS t2 SET t1.deletedLocaltime = DATE_ADD(UTC_TIMESTAMP(), INTERVAL (3600 * t2.userTimezoneOffset) SECOND)" +
                                " WHERE slackMessageId = ?;")
                ps.setString(1,message.slackMessageID)
                ps.setString(2,message.slackMessageID)
                ps.executeUpdate()
                ps.close()


            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }


    fun hearthBeat(projectName: String, heartBeat: Boolean) {
        try {
            val conn = getConnection()
            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE).use { stmt ->
                val date: Date = Date()
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

}