/**
 * Created by Mateusz on 23/05/2017.
 */

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.SlackUser
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory

import java.io.IOException
import java.util.*

/**
 * Created by Mateusz on 14/11/2016.
 */
class HTTPServer(var keys:Keys,var sessionNonAdmin: SlackSession,var server: HttpServer,var timer: Timer) : HttpHandler {


    /***
     * Method used for purpose of receiving HTTP requests to add users to Slack and to add users to Slack groups
     * @param t used to handle http requests
     * *
     * @throws IOException
     */
    @Throws(IOException::class)
    override fun handle(t: HttpExchange) {

        println("Creating admin Session")
        val session = SlackSessionFactory.createWebSocketSlackSession(keys.slackAdminKey)
        session.connect()

        val response = t.requestURI.path
        t.sendResponseHeaders(200, 0)
        val os = t.responseBody
        os.write(response.toByteArray())
        os.close()
        val db = DatabaseManager(keys)
        if (response.contains("group")) {
            addUsersToGroup(session, db)
        } else if (response.contains("invite")) {
            inviteUsersToSlack(session, db)
        } else if (response.contains("goodnight")) {
            shutdown(db)
        }

    }

    /***
     * Method used for purpose of inviting users to Slack
     * @param session used for communicating with slack API
     * *
     * @param db used to query database
     */
    @Throws(IOException::class)
    private fun inviteUsersToSlack(session: SlackSession, db: DatabaseManager) {
        val slackMessageArrayList = db.getUsersToInvite()
        val slackUserArrayList = ArrayList(session.users)
        for (slackMessage in slackMessageArrayList) {


            var userFound = false
            for (slackUser in slackUserArrayList) {
                System.out.println("user to invite " + slackMessage.email)
                println(slackUser.userMail)
                if (slackMessage.email == slackUser.userMail) {
                    System.out.println("user found " + slackMessage.email)
                    userFound = true
                    break
                }
            }
            if (!userFound) {
                session.inviteUser(slackMessage.email, slackMessage.firstName, true)
                println("Following user was invited : ${slackMessage.firstName} with email address ${slackMessage.email}")
            }
        }
        session.disconnect()
    }

    /***
     * Method used for purpose of adding users to groups of Slack
     * @param session used for communicating with slack API
     * *
     * @param db used to query database
     */
    @Throws(IOException::class)
    private fun addUsersToGroup(session: SlackSession, db: DatabaseManager) {
        val slackMessageArrayList = db.getUserGroup()
        val slackChannelArrayList = ArrayList(session.channels)
        val slackUserArrayList = ArrayList(session.users)

        for (slackMessage in slackMessageArrayList) {
            var slackChannelTemp: SlackChannel? = null
            var slackUserTemp: SlackUser? = null
            for (slackChannel in slackChannelArrayList) {
                if (slackChannel.id == slackMessage.channelID) {
                    slackChannelTemp = slackChannel
                    break
                }
            }
            for (slackUser in slackUserArrayList) {
                if (slackUser.id == slackMessage.userID) {
                    slackUserTemp = slackUser
                }
            }
            if (slackChannelTemp != null && slackUserTemp != null) {
                session.inviteToChannel(slackChannelTemp, slackUserTemp)
            }
        }
        session.disconnect()
    }

    private fun shutdown(db: DatabaseManager) {
        sessionNonAdmin.disconnect()
        timer.cancel()
        db.hearthBeat(keys.projectName,false)
        server.stop(0)
        System.exit(0)
    }


}