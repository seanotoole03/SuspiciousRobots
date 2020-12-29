import java.lang.reflect.Array;
import java.util.*;

import javax.lang.model.util.Elements.Origin;

/** This is the base class for computer player/bots. 
 * 
 */

public class RBotEfficient extends Bot
{
    public static int ply = 1;

    Random r = new Random();
    TextDisplay display;

    public static GameState currentState;    
    public static float viewDeckAvg = 0;
    public static boolean viewDeckCard = false;
    
    List<String> variables;
    Map<String, List<String>> domains;
    CSP<String, String> csp;

    // int[] myGemCounts = new int[3];

    public static class GameState
    {
        public String boardString;
        public Board board;
        public int[] myGemCounts = new int[3]; //tracking my personal count of gems-- direct replacement for myGemCounts
        public int[] gameGemCounts = new int[3]; //tracking overall count of gems taken/remaining by all players (depending on chosen approach)
        public HashMap<String, Player> players; // Keyed off of player name
        public String otherPlayerNames[];
        public HashMap<String, Piece> pieces; // Keyed off of guest name
        public Piece me;
        public String gemLocations;
        public GameState(String strboard, int[] mgc, int[] ggc, HashMap<String,Player> inp, String[] inopn, HashMap<String, Piece> ip, Piece inm, String ingl) {
            myGemCounts = mgc.clone();
            gameGemCounts = ggc.clone();
            players = new HashMap<String, Player>();
            for(String key : inp.keySet()){ //deep clone of players
                Player p = inp.get(key).deepClone();
                players.put(key, p);
            }
            otherPlayerNames = inopn.clone();
            pieces = new HashMap<String, Piece>();
            for(String key : ip.keySet()){
                pieces.put(key,(ip.get(key)));
            }
            me = inm;
            board = new Board(strboard, ip, ingl);
            boardString = strboard;
        }

        public GameState(HashMap<String,Player> inp, String[] inopn, HashMap<String, Piece> ip, Piece inm, String ingl, int[] mgc, int[] ggc) {
            myGemCounts = mgc.clone();
            gameGemCounts = ggc.clone();
            players = new HashMap<String, Player>();
            for(String key : inp.keySet()){ //deep clone of players
                players.put(key, inp.get(key).deepClone());
            }
            otherPlayerNames = inopn.clone();
            pieces = new HashMap<String, Piece>();
            for(String key : ip.keySet()){
                pieces.put(key, ip.get(key));
            }
            me = inm;
            gemLocations = ingl;
            //board left uninitialized
        }

        //initialization GS
        public GameState(HashMap<String,Player> inp, String[] inopn, HashMap<String, Piece> ip, Piece inm, String ingl) {
            if(myGemCounts.length == 0) myGemCounts = new int[3];
            gameGemCounts = new int[3]; //should be updated with board information at some point
            players = new HashMap<String, Player>();
            for(String key : inp.keySet()){ //deep clone of players
                players.put(key, inp.get(key).deepClone());
            }
            otherPlayerNames = inopn.clone();
            pieces = new HashMap<String, Piece>();
            for(String key : ip.keySet()){
                pieces.put(key, ip.get(key));
            }
            me = inm;
            gemLocations = ingl;
            //board left uninitialized
        }

        public GameState cloneState(){
            if(boardString != null)
                return new GameState(boardString,myGemCounts,gameGemCounts,players,otherPlayerNames,pieces,me,gemLocations);
            else
                return new GameState(players,otherPlayerNames,pieces,me,gemLocations, myGemCounts, gameGemCounts);
        }

        public void updateBoard(String stringBoard){
            board = new Board(stringBoard, pieces, board.gemLocations);
        }
    }

    public static class Board
    {
        public Room rooms[][];
        public String gemLocations;

        public class Room
        {
            public final boolean gems[] = new boolean[3];
            public final String[] availableGems;
            public final int row;
            public final int col;
            public HashMap<String, Piece> pieces;

            public void removePlayer(Piece piece)
            {
                removePlayer(piece.name);
                piece.col=-1;
                piece.row=-1;
            }

            public void removePlayer(String name)
            {
                pieces.remove(name);
            }
            
            public void addPlayer(Piece piece)
            {
                piece.col=this.col;
                piece.row=this.row;
                pieces.put(piece.name, piece);
            }

            public Room(boolean red, boolean green, boolean yellow, int row, int col)
            {
                pieces = new HashMap<String, Piece>();
                this.row = row;
                this.col = col;
                gems[Suspicion.RED]=red;
                gems[Suspicion.GREEN]=green;
                gems[Suspicion.YELLOW]=yellow;
                String temp="";
                if(red) temp += "red,";
                if(green) temp += "green,";
                if(yellow) temp += "yellow,";
                availableGems = (temp.substring(0,temp.length()-1)).split(",");
            }
        }

        public void movePlayer(Piece player, int row, int col)
        {
            rooms[player.row][player.col].removePlayer(player);
            rooms[row][col].addPlayer(player);
        }
        
        public void clearRooms()
        {
            rooms=new Room[3][4];
            int x=0, y=0;
            boolean red, green, yellow;
        
            for(String gems:gemLocations.trim().split(":"))
            {
                if(gems.contains("red")) red=true;
                else red=false;
                if(gems.contains("green")) green=true;
                else green=false;
                if(gems.contains("yellow")) yellow=true;
                else yellow=false;
                rooms[x][y] = new Room(red,green,yellow,x,y);
                y++;
                x += y/4;
                y %= 4;
            }
        }

        public Board(String piecePositions, HashMap<String, Piece> pieces, String gemLocations)
        {
            Piece piece;
            this.gemLocations=gemLocations;
            clearRooms();
            int col=0;
            int row=0;
            for(String room:piecePositions.split(":",-1)) // Split out each room
            {
                room = room.trim();
                if(room.length()!=0) for(String guest: room.split(",")) // Split guests out of each room
                {
                    guest = guest.trim();
                    piece = pieces.get(guest);
                    rooms[row][col].addPlayer(piece);
                }
                col++;
                row = row + col/4;
                col = col%4;
            }
        }
    }

    public Piece getPiece(String name)
    {
        return currentState.pieces.get(name);
    }

    public class Player
    {
        public String playerName;
        public ArrayList<String> possibleGuestNames;
        int[] playerGemCounts = new int[3];
        
        public void adjustKnowledge(ArrayList<String> possibleGuests)
        {
            Iterator<String> it = possibleGuestNames.iterator();
            while(it.hasNext())
            {
                String g;
                if(!possibleGuests.contains(g=it.next())) 
                {
                    it.remove();
                }
            }
        }

        public void adjustKnowledge(String notPossibleGuest)
        {
            Iterator<String> it = possibleGuestNames.iterator();
            while(it.hasNext())
            {
                if(it.next().equals(notPossibleGuest)) 
                {
                    it.remove();
                    break;
                }
            }
        }

        public Player(String name, String[] guests)
        {
            playerName = name;
            possibleGuestNames = new ArrayList<String>();
            for(String g: guests)
            {
                possibleGuestNames.add(g);
            }
        }

        public Player deepClone(){
            return new Player(this.playerName, possibleGuestNames.toArray(new String[possibleGuestNames.size()]));
        }
    }

    public class Piece
    {
        public int row, col;
        public String name;

        public Piece(String name)
        {
            this.name = name;
        }
        public Piece deepClone(){
            Piece ret = new Piece(this.name);
            ret.row = this.row;
            ret.col = this.col;
            return ret;
        }
    }

    private String[] getPossibleMoves(Piece p)
    {
        LinkedList<String> moves=new LinkedList<String>();
        if(p.row > 0) moves.push((p.row-1) + "," + p.col);
        if(p.row < 2) moves.push((p.row+1) + "," + p.col);
        if(p.col > 0) moves.push((p.row) + "," + (p.col-1));
        if(p.col < 3) moves.push((p.row) + "," + (p.col+1));

        return moves.toArray(new String[moves.size()]);
    }

    public float computeGemScore(int[] gems) {
        float gemScore = gems[0] + gems[1] + gems[2]; 
            int min = gems[0];
            if(gems[1] < min) min = gems[1];
            if(gems[2] < min) min = gems[2];
            gemScore += 3 * min;
        return gemScore;
    }

    public int chooseBestCard(String card1, String card2) {
        boolean flag = true;
        int tempCount = 0;
        for (String pname : currentState.players.keySet()) {
            if (currentState.players.get(pname).possibleGuestNames.size()<3) {
                tempCount += 1;
                if (tempCount>3)
                    flag = false;
                    break;
            }
        }
        int choosenCard=2;
        
        float ut1 = -1;
        float ut2 = -1;

        for (String action: card1.split(":")) {
            if (action.startsWith("get")) {
                if (action.split(",").length > 1) {
                    choosenCard = 0;
                }
            }
            else if (action.startsWith("ask")) {
                ut1 = Float.parseFloat(getBestPlayerToAsk(currentState, action).split(";")[1]);
            } else if (action.startsWith("viewDeck")) {
                if (ut1 < 1 && flag) {
                    choosenCard = 0;
                }
            }
        }
        for (String action: card2.split(":")) {
            if (action.startsWith("get")) {
                if (action.split(",").length > 1) {
                    if (choosenCard==0 && Math.abs(ut1-1.1)<0.001f)
                        choosenCard = 2;
                }
            }
            else if (action.startsWith("ask")) {
                ut2 = Float.parseFloat(getBestPlayerToAsk(currentState, action).split(";")[1]);
            } else if (action.startsWith("viewDeck")) {
                if (ut2 < 1 && flag) {
                    choosenCard = 1;
                }
            }
        }
        // System.out.println(card1);
        // System.out.println(card2);
        // System.out.println("-");
        if (choosenCard==2 && Math.round(ut1) != -1 && Math.round(ut2) != -1) {
            // System.out.println("UT1: "+ut1 +"  UT2: "+ ut2 );
            choosenCard = (ut1>ut2) ? 0 : 1;
        }
        // System.out.println("-");
        if (choosenCard == 2) {
            choosenCard = r.nextInt(2);
            if (card1.contains("get"))
                choosenCard = 0;
            if (card2.contains("get"))
                choosenCard = 1;
        }
        return choosenCard;
    }
    /**
     * Calculate
     * @param original
     * @param modified
     * @param player
     * @return
     */
    public float expectedUtility(GameState original, GameState modified, String player) {
        Set<String> pSet = new HashSet<String>();
        float scoreWeightO = 1;
        float scoreWeightM = 1;
        for (String pname: modified.players.keySet()) {
            if (modified.players.get(pname).possibleGuestNames.size()<=1) {
                if (modified.players.get(pname).possibleGuestNames.size()==1) {
                    scoreWeightM += 0.1;
                    if (!pSet.add(modified.players.get(pname).possibleGuestNames.get(0))) {
                        return -1;
                    }
                } else if (modified.players.get(pname).possibleGuestNames.size()==0) {
                    return -1;
                }
            }
            if (original.players.get(pname).possibleGuestNames.size()==1) {
                scoreWeightO += 0.1;
            }
        }

        float oGuessScore = getOptimizedGuessScore(original.players).get("total");
        float mGuessScore = getOptimizedGuessScore(modified.players).get("total");
        float expectedGainFromKB = scoreWeightM*mGuessScore - scoreWeightO*oGuessScore;
        if(viewDeckCard) expectedGainFromKB += viewDeckAvg;
        float expectedScoreGain=0;
        float expectedGainFromGem = computeGemScore(modified.myGemCounts) - computeGemScore(original.myGemCounts);
        expectedScoreGain = expectedGainFromKB + expectedGainFromGem;
        return expectedScoreGain;
    }

    public String getPlayerActions(String d1, String d2, String board) throws Suspicion.BadActionException
    {
        //use for other players/generic players?
        return "This is terrifying";
    }

    public void movePlayer(GameState gs, Piece p, int row, int col) {
        Piece newPiece = p.deepClone();
        newPiece.row = row;
        newPiece.col = col;
        gs.pieces.put(p.name, newPiece);
    }

    public void chooseBestMovesAndCard(String d1, String d2, String card1, String card2) throws Suspicion.BadActionException {

        // System.out.println(d1);
        // System.out.println(d2);
        // System.out.println(card1);
        // System.out.println(card2);
    }


    public String getPlayerActions(String d1, String d2, String card1, String card2, String board) throws Suspicion.BadActionException
    {
        currentState.board = new Board(board, currentState.pieces, gemLocations);
        currentState.updateBoard(board);
        // chooseBestMovesAndCard(d1, d2, card1, card2);
        // GameState modified = currentState.cloneState();

        //at this point, we begin the utility function and begin exploring our knowledge base and all possible options for actions

        String actions = "";

        //need to optimize choice of piece
        if(d1.equals("?")) d1 = guestNames[r.nextInt(guestNames.length)];

        Piece piece = currentState.pieces.get(d1);
        String[] moves = getPossibleMoves(piece);

        //select from possible moves (for loop over options?)
        int movei = r.nextInt(moves.length);

        actions += "move," + d1 + "," + moves[movei];
        currentState.board.movePlayer(piece, Integer.parseInt(moves[movei].split(",")[0]), Integer.parseInt(moves[movei].split(",")[1])); // Perform the move on my board

        // Random move for dice2
        //need to optimize choice of piece
        if(d2.equals("?")) d2 = guestNames[r.nextInt(guestNames.length)];

        piece = currentState.pieces.get(d2);
        moves = getPossibleMoves(piece);
        //select from possible moves 
        movei = r.nextInt(moves.length);
        actions += ":move," + d2 + "," + moves[movei];
        currentState.board.movePlayer(piece, Integer.parseInt(moves[movei].split(",")[0]), Integer.parseInt(moves[movei].split(",")[1])); // Perform the move on my board

        //DETERMINE EXPECTED UTILITY HERE??? HEAVEN HELP US
        // select which card to play
        int i = chooseBestCard(card1, card2);
        actions += ":play,card"+(i+1);

        String card = i==0?card1:card2;

        for(String cardAction: card.split(":")) // just go ahead and do them in this order
        {
            if(cardAction.startsWith("move"))
            {
                if (card.split(":")[1].startsWith("ask")) {
                    int choosenRow = -1;
                    int choosenCol = -1;
                    String choosenGuest = "";
                    GameState original = currentState.cloneState();
                    float highestUtility = -1;
                    float utilityByAskingO = Float.parseFloat(getBestPlayerToAsk(original, card.split(":")[1]).split(";")[1]);
                    
                    for (int j = 0; j < guestNames.length; j++) {
                        String guest = guestNames[j];
                        piece = currentState.pieces.get(guest);
                        for (int r = 0; r < 3; r++) {
                            for (int c = 0; c < 4; c++) {
                                GameState modified = currentState.cloneState();
                                movePlayer(modified, piece, r, c);
                                float utilityByAskingM = Float.parseFloat(getBestPlayerToAsk(modified, card.split(":")[1]).split(";")[1]);
                                float utility = utilityByAskingM - utilityByAskingO;
                                if (utility>highestUtility) {
                                    choosenRow = r;
                                    choosenCol = c;
                                    choosenGuest = guest;
                                }
                            }
                        }
                    }
                    actions += ":move," + choosenGuest + "," + choosenRow + "," + choosenCol;
                } else {
                    String guest;
                    guest = guestNames[r.nextInt(guestNames.length)];
                    piece = currentState.pieces.get(guest);
                    //moves = getPossibleMoves(piece);
                    actions += ":move," + guest + "," + r.nextInt(3) + "," + r.nextInt(4);
                }
            }
            else if(cardAction.startsWith("viewDeck")) 
            {
                actions += ":viewDeck";
            }
            else if(cardAction.startsWith("get"))
            {
                // int loc = 4 * me.row + me.col;
                HashMap<String, Set<String>> gemPlayer = new HashMap<>();
                String gemToGrab = "";
                
                String[] gbrd = gemLocations.split(":");
                for (int j=0; j<gbrd.length; j++) {
                    for (String g: gbrd[j].split(",")) {
                        for (String kp: currentState.pieces.keySet()) {
                            Piece p = currentState.pieces.get(kp);
                            int ploc = 4 * p.row + p.col;
                            if (j==ploc) {
                                if (gemPlayer.get(g) == null) {
                                    gemPlayer.computeIfAbsent(g, k -> new HashSet<>()).add(p.name);
                                } else {
                                    Set<String> t = new HashSet<>();
                                    t.addAll(gemPlayer.get(g));
                                    t.add(p.name);
                                    gemPlayer.put(g, t);
                                }
                            }
                        }
                    }
                }
                HashMap<String, Integer> gem2Grab = new HashMap<>();
                if(cardAction.equals("get,"))
                {
                    for (String gem : currentState.board.rooms[currentState.me.row][currentState.me.col].availableGems) {
                        if (gem.equals("red")) {
                            if (currentState.myGemCounts[Suspicion.RED] < currentState.myGemCounts[Suspicion.GREEN] && currentState.myGemCounts[Suspicion.RED] < currentState.myGemCounts[Suspicion.YELLOW]) {
                                gemToGrab = gem;
                            } else if (currentState.myGemCounts[Suspicion.RED] <= currentState.myGemCounts[Suspicion.GREEN] || currentState.myGemCounts[Suspicion.RED] <= currentState.myGemCounts[Suspicion.YELLOW]) {
                                if (currentState.myGemCounts[Suspicion.RED] == currentState.myGemCounts[Suspicion.GREEN] || currentState.myGemCounts[Suspicion.RED] == currentState.myGemCounts[Suspicion.YELLOW]) {
                                    gem2Grab.put(gem, gemPlayer.get(gem).size());
                                } else {
                                    if (gemPlayer.get(gem).size()>4) {
                                        gemToGrab = gem;
                                    } else {
                                        gem2Grab.put(gem, gemPlayer.get(gem).size());
                                    }
                                }
                            }
                        } else if (gem.equals("green")) {
                            if (currentState.myGemCounts[Suspicion.GREEN] < currentState.myGemCounts[Suspicion.RED] && currentState.myGemCounts[Suspicion.GREEN] < currentState.myGemCounts[Suspicion.YELLOW]) {
                                gemToGrab = gem;
                            } else if (currentState.myGemCounts[Suspicion.GREEN] <= currentState.myGemCounts[Suspicion.RED] || currentState.myGemCounts[Suspicion.GREEN] <= currentState.myGemCounts[Suspicion.YELLOW]) {
                                if (currentState.myGemCounts[Suspicion.GREEN] == currentState.myGemCounts[Suspicion.RED] || currentState.myGemCounts[Suspicion.GREEN] == currentState.myGemCounts[Suspicion.YELLOW]) {
                                    gem2Grab.put(gem, gemPlayer.get(gem).size());
                                } else {
                                    if (gemPlayer.get(gem).size()>4) {
                                        gemToGrab = gem;
                                    } else {
                                        gem2Grab.put(gem, gemPlayer.get(gem).size());
                                    }
                                }
                            }
                        } else if (gem.equals("yellow")) {
                            if (currentState.myGemCounts[Suspicion.YELLOW] < currentState.myGemCounts[Suspicion.RED] && currentState.myGemCounts[Suspicion.YELLOW] < currentState.myGemCounts[Suspicion.GREEN]) {
                                gemToGrab = gem;
                            } else if (currentState.myGemCounts[Suspicion.YELLOW] <= currentState.myGemCounts[Suspicion.RED] || currentState.myGemCounts[Suspicion.YELLOW] <= currentState.myGemCounts[Suspicion.GREEN]) {
                                if (currentState.myGemCounts[Suspicion.YELLOW] == currentState.myGemCounts[Suspicion.RED] || currentState.myGemCounts[Suspicion.YELLOW] == currentState.myGemCounts[Suspicion.GREEN]) {
                                    gem2Grab.put(gem, gemPlayer.get(gem).size());
                                } else {
                                    if (gemPlayer.get(gem).size()>4) {
                                        gemToGrab = gem;
                                    } else {
                                        gem2Grab.put(gem, gemPlayer.get(gem).size());
                                    }
                                }
                            }
                        }
                    }

                    int big = 0;
                    if (gemToGrab.isEmpty()) {
                        for (String g : gem2Grab.keySet()) {
                            if (gem2Grab.get(g).intValue() > big) {
                                big = gem2Grab.get(g);
                                gemToGrab = g;
                            }
                        }
                    } 
                    if (gemToGrab.isEmpty()) {
                        // Grab a random gem
                        gemToGrab = currentState.board.rooms[currentState.me.row][currentState.me.col].availableGems[r.nextInt(currentState.board.rooms[currentState.me.row][currentState.me.col].availableGems.length)];
                    }
                    actions += ":get," + gemToGrab;
                }
                else 
                {
                    actions += ":" + cardAction;
                    gemToGrab=cardAction.trim().split(",")[1];
                }
                if(gemToGrab.equals("red")) currentState.myGemCounts[Suspicion.RED]++;
                else if(gemToGrab.equals("green")) currentState.myGemCounts[Suspicion.GREEN]++;
                else currentState.myGemCounts[Suspicion.YELLOW]++;
            }
            else if(cardAction.startsWith("ask"))
            {
                String bestPlayerToAsk = getBestPlayerToAsk(currentState, cardAction).split(";")[0];
                actions += ":" + cardAction + bestPlayerToAsk;
                // actions += ":" + cardAction + otherPlayerNames[r.nextInt(otherPlayerNames.length)]; 
            }
        }
       // System.out.println("\n"+actions+"\n");
        return actions;
    }

    private HashMap<String, Float> getOptimizedGuessScore(HashMap<String, Player> KB) {
        HashMap<String, Float> KBGuessScore = new HashMap<String, Float>();
        float totalGScore = 0;
        for (String pname: KB.keySet()) {
            
            float p = (float) 1 / KB.get(pname).possibleGuestNames.size(); //non-bayesian base
            float gScore = p * 7f;
            //bayesian rough
            float bay = 0, max = 0;
            float countCollide = 0;
            ArrayList<Float> f = new ArrayList<Float>();
            for(String guestName : KB.get(pname).possibleGuestNames)
            {
                for(String opname : KB.keySet())
                {
                    if(!pname.equals(opname))
                    {
                        if(KB.get(opname).possibleGuestNames.contains(guestName))
                        { //adjust base probability down
                            countCollide++;
                        }
                    }
                }
                bay = p / (1f+countCollide);
                if(bay > max) max = bay;
                f.add(Float.valueOf(bay));
                countCollide = 0;
            }
            float sum = 0; for(float a : f) { sum += a; }
            if(sum < 1 && sum != 0){
                max = max / sum; //max is based on overall probability post-conditional-adjustments
            }
            gScore = max * 7f; //overwrite with chosen max probability, since that's the one we would rationally pick
            totalGScore += gScore;
            KBGuessScore.put(pname, gScore);
        }
        KBGuessScore.put("total", totalGScore);
        return KBGuessScore;
    }

    private HashMap<String, Float> getOptimizedEntropy(HashMap<String, Player> KB) {
        float total = 1;

        for (String pname: KB.keySet()) {
            total *= KB.get(pname).possibleGuestNames.size();
        }

        float entropy = 0;//, totalEntropy = 0, largestEntropy=0;
        HashMap<String, Float> KBEntropy = new HashMap<String, Float>();
        
        for (String pname: KB.keySet()) {
            float p = KB.get(pname).possibleGuestNames.size();
            if (total != 0) entropy = -p/total * (float) (Math.log(p/total) / Math.log(2));
            // if (entropy > largestEntropy) largestEntropy = entropy;
            // totalEntropy += entropy;
            KBEntropy.put(pname, entropy);
        }
        // KBEntropy.put("total", totalEntropy);
        // KBEntropy.put("largest", largestEntropy);
        return KBEntropy;
    }

    private String getBestPlayerToAsk(GameState gs, String cardAskAction) {
        Piece p = gs.pieces.get(cardAskAction.split(",")[1]);

        float highestUtility = 0;
        String rrval = "";

        for (String pname : gs.players.keySet()) {
            GameState yesState = gs.cloneState(), noState = gs.cloneState();
            float n = 0, y = 0;

            for (String k: gs.pieces.keySet()) {
                Piece tp = gs.pieces.get(k);
                // If answered "YES"
                if (!canSee(tp, p)) {
                    yesState.players.get(pname).possibleGuestNames.remove(tp.name);
                    y+=1;
                } else { // If answered "NO"
                    noState.players.get(pname).possibleGuestNames.remove(tp.name);
                    n+=1;
                }
            }
            
            float PY = y / (y+n);
            float PN = n / (y+n);
            
            float utilityY = expectedUtility(gs, yesState, "");
            float utilityN = expectedUtility(gs, noState, "");
            float utility = PY*utilityY + PN*utilityN;

            if (utility > highestUtility) {
                highestUtility = utility;
                rrval = pname;
            }
            //refresh states for next player?
            yesState = currentState.cloneState();
            noState = currentState.cloneState();
        }
        
        if (rrval.isEmpty()) rrval = gs.otherPlayerNames[2]; //gotta optimize here
        // System.out.println("RR: " + rrval);
        return rrval + ";" + highestUtility;

        // int biggest = currentState.players.get(currentState.otherPlayerNames[0]).possibleGuestNames.size();
        // String rval = currentState.otherPlayerNames[0];

        // for (String name : currentState.otherPlayerNames)
        // {
        //     if (currentState.players.get(name).possibleGuestNames.size() > biggest) 
        //     {
        //         biggest = currentState.players.get(name).possibleGuestNames.size();
        //         rval = name;
        //     }
        // }
        // System.out.println("R: "+rval);
        // return rval;
    }

    ArrayList<String> singleList = new ArrayList<>();
    int prevCount = 0;
    private void elimSingleton() {
        for (String pname : currentState.otherPlayerNames) {
            if (currentState.players.get(pname).possibleGuestNames.size() == 1) {
                singleList.add(currentState.players.get(pname).possibleGuestNames.get(0));
            } else {
                if (singleList.size() > 0) {
                    for (String sname: singleList) {
                        if (currentState.players.get(pname).possibleGuestNames.contains(sname)) {
                            currentState.players.get(pname).possibleGuestNames.remove(sname);
                        }
                    }
                }
            }
        }
        if (singleList.size() == prevCount) {
            prevCount = 0;
            return;
        }
        prevCount = singleList.size();
        elimRecursive();
    }

    private void elimRecursive() {
        ArrayList<String> tempList = new ArrayList<>(singleList);
        for (String sname: tempList) {
            for (String pname : currentState.otherPlayerNames) {
                if (currentState.players.get(pname).possibleGuestNames.contains(sname) && currentState.players.get(pname).possibleGuestNames.size() > 1) {
                    currentState.players.get(pname).possibleGuestNames.remove(sname);
                }
            }
            singleList.remove(sname);
        }
        elimSingleton();
    }


    // private int countGems(String gem)
    // {
    //     if(gem.equals("red")) return currentState.myGemCounts[Suspicion.RED];
    //     else if(gem.equals("green")) return currentState.myGemCounts[Suspicion.GREEN];
    //     else return currentState.myGemCounts[Suspicion.YELLOW];
    // }

    private ArrayList<String> getGuestsInRoomWithGem(String board, String gemcolor)
    {
        Board b = new Board(board, currentState.pieces, gemLocations);
        int gem=-1;
        if(gemcolor.equals("yellow")) gem = Suspicion.YELLOW;
        else if(gemcolor.equals("green")) gem = Suspicion.GREEN;
        else if(gemcolor.equals("red")) gem = Suspicion.RED;
        ArrayList<String> possibleGuests = new ArrayList<String>();

        int y=0,x=0;
        for(String guests: board.trim().split(":"))
        {
            //only get people from rooms with the gem
            if(b.rooms[y][x].gems[gem] && guests.trim().length()>0)
            {
                for(String guest:guests.trim().split(","))
                {
                    possibleGuests.add(guest.trim());
                }
            }
            x++;
            y+=x/4;
            x%=4;
        }
        
        return possibleGuests;
    }

    public void reportPlayerActions(String player, String d1, String d2, String cardPlayed, String board, String actions)
    {
        //empty when provided
    }
    Set<String> singletonTemp = new HashSet<>();
    public void reportPlayerActions(String player, String d1, String d2, String cardPlayed, String board[], String actions)
    {
        for (String pname: currentState.players.keySet()) {
            // System.out.println(pname);
            // System.out.println(currentState.players.get(pname).possibleGuestNames);
            if (currentState.players.get(pname).possibleGuestNames.size()==1) {
                if (singletonTemp.add(currentState.players.get(pname).possibleGuestNames.get(0))) {
                    elimSingleton();
                }
            }
        }
        // System.out.println("-----------------------------------------------------");
        if(player.equals(this.playerName)) return; // If player is me, return
        // Check for a get action and use the info to update player knowledge
        if(cardPlayed.split(":")[0].equals("get,") || cardPlayed.split(":")[1].equals("get,"))
        {
            int splitindex;
            String[] split = actions.split(":");
            String get;
            if(split[3].indexOf("get")>=0) splitindex=3;
            else splitindex=4;
            get=split[splitindex];
            String gem = get.split(",")[1];
            // board[splitIndex+1] will have the state of the board when the gem was taken
            if(board[splitindex]!=null) // This would indicate an error in the action
            {
                ArrayList<String> possibleGuests = getGuestsInRoomWithGem(board[splitindex],gem);
                currentState.players.get(player).adjustKnowledge(possibleGuests);
            }
        }
    }

    private boolean canSee(Piece p1, Piece p2) // returns whether or not these two pieces see each 
    {
        return (p1.row==p2.row || p1.col == p2.col);
    }

    
    public void answerAsk(String guest, String player, String board, boolean canSee)
    {
        Board b = new Board(board, currentState.pieces, gemLocations);
        ArrayList<String> possibleGuests = new ArrayList<String>();
        Piece p1 = currentState.pieces.get(guest);  // retrieve the guest 
        for(String k : currentState.pieces.keySet())
        {
            Piece p2 = currentState.pieces.get(k);
            if((canSee && canSee(p1,p2)) || (!canSee && !canSee(p1,p2))) possibleGuests.add(p2.name);
        }
        currentState.players.get(player).adjustKnowledge(possibleGuests);
    }

    public void answerViewDeck(String player)
    {
        for(String k:currentState.players.keySet())
        {
            currentState.players.get(k).adjustKnowledge(player);
        }
    }
     /**
     * CSP code copied from livebook.manning.com
     */
    public abstract class Constraint <V, D> {
        protected List <V> variables;
        
        public Constraint(List<V> variables) {
            this.variables = variables;
        }
        public abstract boolean satisfied(Map<V, D> assignment);
    }

    public class CSP<V, D> {
        private List<V> variables;
        private Map<V, List<D>> domains;
        private Map<V, List<Constraint<V, D>>> constraints = new HashMap<>();

        public CSP(List<V> variables, Map<V, List<D>> domains) {
            this.variables = variables;
            this.domains = domains;
            for (V variable: variables) {
                constraints.put(variable, new ArrayList<>());
                if (!domains.containsKey(variable)) {
                    throw new IllegalArgumentException("Every variable should have a domain assigned to it.");
                }
            }
        }

        public void printConstraints() {
            for (V constraint : this.constraints.keySet()) {
                System.out.println(constraint);
                for (Constraint<V,D> a : this.constraints.get(constraint)) {
                    System.out.print("-" + a.variables + "-");
                }
                System.out.println();
            }
        }
        
        public void addConstraint(Constraint<V, D> constraint) {
            for (V variable: constraint.variables) {
                if (!variables.contains(variable)) {
                    throw new IllegalArgumentException("Variable in constraint not in CSP");
                }
                constraints.get(variable).add(constraint);
            }
        }

        public boolean consistent(V variable, Map<V, D> assignment) {
            for (Constraint<V, D> constraint: constraints.get(variable)) {
                if (!constraint.satisfied(assignment)) {
                    return false;
                }
            }
            return true;
        }

        public Map<V, D> backtrackingSearch(Map<V, D> assignment) {
            if (assignment.size() == variables.size()) {
                return assignment;
            }
            V unassigned = variables.stream().filter(v -> !assignment.containsKey(v)).findFirst().get();
            for (D value: domains.get(unassigned)) {
                Map<V, D> localAssignemnt = new HashMap<>(assignment);
                localAssignemnt.put(unassigned, value);
                if (consistent(unassigned, localAssignemnt)) {
                    Map<V, D> result = backtrackingSearch(localAssignemnt);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        }
        public Map<V, D> backtrackingSearch() {
            return backtrackingSearch(new HashMap<>());
        }
    }

    public final class SuspicionConstraint extends Constraint<String, String> {
        private String p1, p2;
     
        public SuspicionConstraint(String p1, String p2) {
            super(Arrays.asList(p1, p2));
            this.p1 = p1;
            this.p2 = p2;
        }
     
        @Override
        public boolean satisfied(Map<String, String> assignment) {
            if (!assignment.containsKey(p1) || !assignment.containsKey(p2)) {
                return true;
            }
            return !assignment.get(p1).equals(assignment.get(p2));
        }
    }
    
    public HashMap<String, List<String>> suspicionCSP() {
        List<String> variables = new LinkedList<>(Arrays.asList(playerNames));
        variables.remove(playerName);
        Map<String, List<String>> domains = new HashMap<>();
        
        for (String k:currentState.players.keySet()) {
            domains.put(k, currentState.players.get(k).possibleGuestNames);
        }
        CSP<String, String> csp = new CSP<>(variables, domains);

        for (String k1:variables) {
            for (String k2:variables) {
                if (k1 != k2) {
                    csp.addConstraint(new SuspicionConstraint(k1, k2));
                }
            }
        }

        Map<String, String> solution = csp.backtrackingSearch();
        
        HashMap<String, List<String>> csp_kb = new HashMap<String, List<String>>();

        for (String k : solution.keySet()) {
            csp_kb.put(k, Arrays.asList(solution.get(k)));
        }
        return csp_kb;
    }

    /* Modify this method to do something more intelligent. */
    public String reportGuesses()
    {
        HashMap<String, List<String>> my_kb = suspicionCSP();
        String rval="";
        for (String k : my_kb.keySet()) {
            rval += k;
            for (String each: my_kb.get(k)) {
                rval += "," + each;
            }
            rval += ":";
        }
        return rval.substring(0,rval.length()-1);
    }

    public RBotEfficient(String playerName, String guestName, int numStartingGems, String gemLocations, String[] playerNames, String[] guestNames)
    {
        super(playerName, guestName, numStartingGems, gemLocations, playerNames, guestNames);
        display = new TextDisplay(gemLocations);
        HashMap<String, Piece> pieces = new HashMap<String, Piece>();
        ArrayList<String> possibleGuests = new ArrayList<String>();
        for(String name:guestNames)
        {
            pieces.put(name, new Piece(name));
            if(!name.equals(guestName)) possibleGuests.add(name);
        }
        Piece me = pieces.get(guestName);

        HashMap<String, Player> players = new HashMap<String, Player>();
        for(String str: playerNames)
        {
            if(!str.equals(playerName)) players.put(str, new Player(str, possibleGuests.toArray(new String[possibleGuests.size()])));
        }

        String[] otherPlayerNames = players.keySet().toArray(new String[players.size()]);
        currentState = new GameState(players, otherPlayerNames, pieces, me, gemLocations);
    }
}
