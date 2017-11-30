package surveyCalculators;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

public class PAEI {

    //ArrayList of keySets
    private final ArrayList<String[][]> sets = new ArrayList<>();
    private String[] keys;

    //Sets up key : value incrementing

    /***
     * Used to seperate the keys for incrementing
     * eg. [["a"],["b", "c"],["d", "e", "f"]]
     *        1        2            3
     * The numbers below corresponds to the response that causes the keys above to be incremented.
     *
     * @param keySets Should be like so [["key_1", ... , "key_n"], ... , keySet_n]
     * @return Returns this, so that functions can be stringed together
     */
    private PAEI q(String[][] keySets) {
        this.sets.add(keySets);
        return this;
    }

    /***
     * Used to get the score of some answers against keys
     * eg. "p" : 4
     *     "j" : 11
     *     ...
     *     "foo" : n
     * Used in outputting survey messages based on the scores of each tested condition [probably better name for it]
     *
     * @param results The int[] answers, where each value is indexed to a question number (-1)
     * @return A map containing k:v for tested conditions / the scores of the conditions
     */
    public HashMap getScore(ArrayList<Integer> results) {

        //Use a map for k:v pairs to replace hard coded variables
        //eg. $p in paei.php
        HashMap<String, Integer> answerTally = new HashMap<>();
        for (String key : keys) {
            answerTally.put(key, 0);
        }
        for (int i = 0; i < results.size(); i++) {
            //Results start at one, so remove one for proper indexing
            int result = results.get(i) - 1;
            String[][] keySets = sets.get(i);

            //For every key specified in the set
            String[] keySet = keySets[result];
            for (String key : keySet) {
                int tally = answerTally.get(key) + 1;
                answerTally.put(key, tally);
            }
        }

        //Return the map containing the scores
        return answerTally;
    }


    /*
    1.) JSON Support (comp)
        --> Regardless of order
        {
            keys : ["a","b",...,"n"]
            1 : [["a"],["b"],["c","d"]]
            ... : ...
            n : [...]
        }
        Processing: Identify object
                    Split at :
                    Separate into arrays
    */
    //Constructor from file
    public PAEI() {
        String fileContents = "{" +
                "\"keys\" : [\"p\", \"a\", \"e\", \"i\"]," +
                "\"1\" : [[\"p\"],[\"a\"]]," +
                "\"2\" : [[\"e\"],[\"i\"]]," +
                "\"3\" : [[\"e\"],[\"p\"]]," +
                "\"4\" : [[\"a\"],[\"i\"]]," +
                "\"5\" : [[\"i\"],[\"p\"]]," +
                "\"6\" : [[\"a\"],[\"e\"]]," +
                "\"7\" : [[\"p\",\"a\"],[\"p\",\"e\"]]," +
                "\"8\" : [[\"a\",\"i\"],[\"e\",\"i\"]]," +
                "\"9\" : [[\"p\",\"a\"],[\"p\",\"i\"]]," +
                "\"10\" : [[\"a\",\"e\"],[\"e\",\"i\"]]," +
                "\"11\" : [[\"p\",\"a\"],[\"a\",\"i\"]]," +
                "\"12\" : [[\"a\",\"e\"],[\"p\",\"a\"]]," +
                "\"13\" : [[\"a\",\"e\"],[\"a\",\"i\"]]," +
                "\"14\" : [[\"e\",\"i\"],[\"p\",\"i\"]]," +
                "\"15\" : [[\"p\",\"a\"],[\"e\",\"i\"]]," +
                "\"16\" : [[\"a\",\"i\"],[\"p\",\"i\"]]," +
                "\"17\" : [[\"p\",\"e\"],[\"p\",\"i\"]]," +
                "\"18\" : [[\"p\",\"e\",\"i\"],[\"a\"]]," +
                "\"19\" : [[\"p\",\"e\"],[\"a\",\"e\"]]," +
                "\"20\" : [[\"e\",\"i\"],[\"p\",\"e\"]]," +
                "\"21\" : [[\"p\",\"e\"],[\"a\",\"i\"]]," +
                "\"22\" : [[\"p\",\"a\",\"e\"],[\"p\",\"a\",\"i\"]]," +
                "\"23\" : [[\"p\",\"e\",\"i\"],[\"a\",\"e\",\"i\"]]," +
                "\"24\" : [[\"p\",\"a\",\"e\"],[\"p\",\"e\",\"i\"]]," +
                "\"25\" : [[\"p\",\"a\",\"i\"],[\"a\",\"e\",\"i\"]]," +
                "\"26\" : [[\"p\",\"a\",\"e\"],[\"a\",\"e\",\"i\"]]," +
                "\"27\" : [[\"p\",\"a\",\"i\"],[\"p\",\"e\",\"i\"]]" +
                "}";

//        try {
//
//            FileReader fr = new FileReader(filePath);
//
//            Scanner in = new Scanner(fr);
//            while (in.hasNextLine()) {
//                fileContents = fileContents.concat(in.nextLine() + "\n");
//
//            }
//            in.close();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
        JSONParser parser = new JSONParser();

        try {
            Iterator<String> stringIter;
            Object obj = parser.parse(fileContents);
            JSONObject jsonObject = (JSONObject) obj;
            JSONArray jsonKeys = (JSONArray) jsonObject.get("keys");
            String[] keys = new String[jsonKeys.size()];
            stringIter = jsonKeys.iterator();
            int i = 0;
            while (stringIter.hasNext()) {
                String ke = stringIter.next();
                keys[i] = ke;
                i++;
            }
            this.keys = keys;
            i = 1;
            while (jsonObject.containsKey(Integer.toString(i))) {
                JSONArray jsonKeySets = (JSONArray) jsonObject.get(Integer.toString(i));
                Iterator<JSONArray> arrayIter = jsonKeySets.iterator();
                String[][] sets = new String[jsonKeySets.size()][];
                int k = 0;
                while (arrayIter.hasNext()) {
                    JSONArray keySets = arrayIter.next();
                    String[] set = new String[keySets.size()];
                    for (int j = 0; j < keySets.size(); j++) {
                        set[j] = (String) keySets.get(j);
                    }
                    sets[k] = set;
                    k++;
                }
                this.q(sets);
                i++;
            }

        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public String[] getKeys() {
        return this.keys;
    }

}