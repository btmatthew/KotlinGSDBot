package objects

class UserActivity : SlackDetails() {
    var welcomeMessage : Int = 0
    var timezoneCheck : Int = 0
    var surveyDummy : Int = 0
    var surveyMBTI : Int = 0
    var surveyPAEI : Int = 0
    var surveyVAK : Int = 0

    fun currentActivity() : Int{

        if(timezoneCheck == 0){
            return 1
        }

        if(surveyDummy == 0){
            return 2
        }

        if(surveyMBTI == 0){
            return 3
        }
        if(surveyPAEI == 0){
            return 4
        }

        if(surveyVAK == 0){
            return 5
        }else{
            return 0
        }
    }


}