package objects

class UserActivity : SlackDetails() {
    var welcomeMessage: Int = 0
    var timezoneCheck: Int = 0
    var surveyDummy: Int = 0
    var surveyMBTI: Int = 0
    var surveyPAEI: Int = 0
    var surveyVARK: Int = 0


    /***
     * Provides the next activity stage
     */
    fun currentActivity(): Int {

        if (timezoneCheck == 0) {
            return 1
        }

        if (surveyDummy == 0) {
            return 2
        }

        if (surveyMBTI == 0) {
            return 3
        }
        if (surveyPAEI == 0) {
            return 4
        }

        return if(surveyVARK==0){
            5
        }else{
            0
        }



    }


}