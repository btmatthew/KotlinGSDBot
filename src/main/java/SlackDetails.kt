/**
 * Created by Mateusz on 22/05/2017.
 */

import java.sql.Timestamp
import java.util.ArrayList

/**
 * Created by Mateusz on 05/10/2016.
 */
class SlackDetails{

    var userName: String = ""
    var message: String = ""
    var timestamp: Timestamp? = null
    var channelName: String = ""
    var status: String = ""
    var channelID: String = ""
    var email: String = ""
    var userID: String = ""
    var slackSideID: String = ""
    var replaces = ArrayList<String>()
    var firstName: String = ""
    var userTimezoneOffset : Double = 0.0
    var userTimezoneLabel : String = ""
    var emojiName : String = ""
    var slackMessageID : String = ""
    var userTimezoneOffsetMilisec : Int = 0
    var threadID : String? = ""
}
