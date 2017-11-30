package surveyCalculators

import java.util.*

class MBTI(val answers: ArrayList<Int> = ArrayList<Int>()) {

    fun mbtiResult() :HashMap<String,Int>{
        val answerTally = HashMap<String, Int>()
        answerTally["e"] = 0
        answerTally["i"] = 0
        answerTally["s"] = 0
        answerTally["n"] = 0
        answerTally["t"] = 0
        answerTally["f"] = 0
        answerTally["j"] = 0
        answerTally["p"] = 0

        for (k in 0..answers.size-1) {
            for (l in 1..7) {
                val sub = (k+1) - l
                if (sub % 7 == 0) {
                    var value : Int
                    when (l) {
                        1 -> if (answers[k]==1){
                            value = answerTally["e"]!!+1
                            answerTally.put("e",value)
                        } else{
                            value = answerTally["i"]!!+1
                            answerTally.put("i",value)
                        }
                        2 -> if (answers[k]==1){
                            value = answerTally["s"]!!+1
                            answerTally.put("s",value)
                        } else{
                            value = answerTally["n"]!!+1
                            answerTally.put("n",value)
                        }
                        3 -> if (answers[k]==1){
                            value = answerTally["s"]!!+1
                            answerTally.put("s",value)
                        } else{
                            value = answerTally["n"]!!+1
                            answerTally.put("n",value)
                        }
                        4 -> if (answers[k]==1){
                            value = answerTally["t"]!!+1
                            answerTally.put("t",value)
                        } else{
                            value = answerTally["f"]!!+1
                            answerTally.put("f",value)
                        }
                        5 -> if (answers[k]==1){
                            value = answerTally["t"]!!+1
                            answerTally.put("t",value)
                        } else{
                            value = answerTally["f"]!!+1
                            answerTally.put("f",value)
                        }
                        6 -> if (answers[k]==1){
                            value = answerTally["j"]!!+1
                            answerTally.put("j",value)
                        } else{
                            value = answerTally["p"]!!+1
                            answerTally.put("p",value)
                        }
                        7 -> if (answers[k]==1){
                            value = answerTally["j"]!!+1
                            answerTally.put("j",value)
                        } else{
                            value = answerTally["p"]!!+1
                            answerTally.put("p",value)
                        }
                    }
                }
            }
        }
        return answerTally
    }
}