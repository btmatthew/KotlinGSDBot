import com.ullink.slack.simpleslackapi.SlackAction
import com.ullink.slack.simpleslackapi.SlackAttachment
import com.ullink.slack.simpleslackapi.SlackField
import com.ullink.slack.simpleslackapi.SlackSession

/**
 * Created by Mateusz on 23/05/2017.
 */
class RespondToUser {

    fun respondToUser(session: SlackSession, channel: String) {
        val slackChannel = session.findChannelById(channel)
        val slackMesage = SlackAttachment()


        slackMesage.text="hello"
        slackMesage.title="This is Survey"
        slackMesage.callbackId

        val action1 : SlackAction = SlackAction()


        action1.text="hello action"
        action1.type="button"
        action1.name="question"
        action1.value="answer1"


        val action2 : SlackAction = SlackAction()
        action2.text="goodbye action"
        action2.type="button"
        action2.name="question"
        action2.value="answer2"

        slackMesage.addAction(action1)
        slackMesage.addAction(action2)


        session.sendMessage(slackChannel, "This is your Survey!",slackMesage)

    }

}