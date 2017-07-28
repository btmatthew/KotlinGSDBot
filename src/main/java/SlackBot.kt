import com.sun.net.httpserver.HttpServer
import com.ullink.slack.simpleslackapi.SlackSessionWrapper
import com.ullink.slack.simpleslackapi.SlackTeam
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import java.net.InetSocketAddress
import java.util.*

/**
 * Created by Mateusz on 22/05/2017.
 */


fun main(args: Array<String>) {
    //Generates a key
    var keys = Keys()

    keys.portNumber = 0//Integer.parseInt(args[0])
    val databaseManager = DatabaseManager(keys)
    keys = databaseManager.getBotDetails()

    //Starts a session

    //val session = SlackSessionFactory.createWebSocketSlackSession(keys.slackKey)
    val session = SlackSessionFactory.createWebSocketSlackSession("")
    session.connect()

    //Checks if the team exists in a database

    val slackTeam: SlackTeam = session.team
    val isTeamInDatabase: Boolean = databaseManager.isTeamInDatabase(slackTeam)

    //Add team if the team doesn't exists in a database
    if (!isTeamInDatabase) {
        databaseManager.addTeamToDatabase(slackTeam)
    }
    //init all the listeners
    val slackMessage = ListeningToMessageEvents(keys)
    slackMessage.registeringAListener(session)
    slackMessage.registeringLoginListener(session)
    slackMessage.registeringChannelCreatedListener(session)
    slackMessage.userTyping(session)
    slackMessage.reaction(session)
    slackMessage.messageEdited(session)
    slackMessage.messageDeleted(session)

    //creates heart beat
    val timer = Timer()
    timer.scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            databaseManager.hearthBeat(keys.projectName,true)
        }
    }, 0, 6 * 10000)

    //Creates http links for interactions
//    val server = HttpServer.create(InetSocketAddress(keys.portNumber), 0)
//    server.createContext("/addusers", HTTPServer(keys, session,server,timer))
//    server.executor = null
//    server.start()


}









