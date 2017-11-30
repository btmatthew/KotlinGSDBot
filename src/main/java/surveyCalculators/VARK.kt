package surveyCalculators

class VARK() {


    private val map: HashMap<Int, HashMap<Char,Char>> = hashMapOf(
            1 to hashMapOf('a' to 'k', 'b' to 'a','c' to 'r','d' to 'v'),
            2 to hashMapOf('a' to 'v', 'b' to 'a','c' to 'r','d' to 'k'),
            3 to hashMapOf('a' to 'k', 'b' to 'v','c' to 'r','d' to 'a'),
            4 to hashMapOf('a' to 'k', 'b' to 'a','c' to 'v','d' to 'r'),
            5 to hashMapOf('a' to 'a', 'b' to 'v','c' to 'k','d' to 'r'),
            6 to hashMapOf('a' to 'k', 'b' to 'r','c' to 'v','d' to 'a'),
            7 to hashMapOf('a' to 'k', 'b' to 'a','c' to 'v','d' to 'r'),
            8 to hashMapOf('a' to 'r', 'b' to 'k','c' to 'a','d' to 'v'),
            9 to hashMapOf('a' to 'r', 'b' to 'a','c' to 'k','d' to 'v'),
            10 to hashMapOf('a' to 'k', 'b' to 'v','c' to 'r','d' to 'a'),
            11 to hashMapOf('a' to 'v', 'b' to 'r','c' to 'a','d' to 'k'),
            12 to hashMapOf('a' to 'a', 'b' to 'r','c' to 'v','d' to 'k'),
            13 to hashMapOf('a' to 'k', 'b' to 'a','c' to 'r','d' to 'v'),
            14 to hashMapOf('a' to 'k', 'b' to 'r','c' to 'a','d' to 'v'),
            15 to hashMapOf('a' to 'k', 'b' to 'a','c' to 'r','d' to 'v'),
            16 to hashMapOf('a' to 'v', 'b' to 'a','c' to 'r','d' to 'k'))

    private val vark : HashMap<Char?,Int> = hashMapOf('v' to 0, 'a' to 0, 'r' to 0, 'k' to 0)

    fun selectQuestion(question : Int, answers: String):HashMap<Char?,Int> {
        val score = map[question]
        if(answers.contains("a")){
            val ch = score?.get('a')
            vark[ch] = 1
        }
        if(answers.contains("b")){
            val ch = score?.get('b')
            vark[ch] = 1
        }
        if(answers.contains("c")){
            val ch = score?.get('c')
            vark[ch] = 1
        }
        if(answers.contains("d")){
            val ch = score?.get('d')
            vark[ch] = 1
        }
        return vark
    }
}