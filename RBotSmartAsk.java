import java.util.*;

/** This is the base class for computer player/bots. 
 * 
 */

public class RBotSmartAsk extends Bot
{
    Random r = new Random();
    HashMap<String, Piece> pieces; // Keyed off of guest name
    Board board;
    Piece me;
    HashMap<String, Player> players; // Keyed off of player name
    String otherPlayerNames[];
    TextDisplay display;

    List<String> variables;
    Map<String, List<String>> domains;
    CSP<String, String> csp;

    int[] gemCounts = new int[3];

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
            private HashMap<String, Piece> pieces;

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
        return pieces.get(name);
    }

    public class Player
    {
        public String playerName;
        public ArrayList<String> possibleGuestNames;
        
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
    }

    public class Piece
    {
        public int row, col;
        public String name;

        public Piece(String name)
        {
            this.name = name;
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

    public String getPlayerActions(String d1, String d2, String card1, String card2, String board) throws Suspicion.BadActionException
    {
        this.board = new Board(board, pieces, gemLocations);
        String actions = "";

        // Random move for dice1
        if(d1.equals("?")) d1 = guestNames[r.nextInt(guestNames.length)];
        Piece piece = pieces.get(d1);
        String[] moves = getPossibleMoves(piece);
        int movei = r.nextInt(moves.length);
        actions += "move," + d1 + "," + moves[movei];
        this.board.movePlayer(piece, Integer.parseInt(moves[movei].split(",")[0]), Integer.parseInt(moves[movei].split(",")[1])); // Perform the move on my board

        // Random move for dice2
        if(d2.equals("?")) d2 = guestNames[r.nextInt(guestNames.length)];
        piece = pieces.get(d2);
        moves = getPossibleMoves(piece);
        movei = r.nextInt(moves.length);
        actions += ":move," + d2 + "," + moves[movei];
        this.board.movePlayer(piece, Integer.parseInt(moves[movei].split(",")[0]), Integer.parseInt(moves[movei].split(",")[1])); // Perform the move on my board

        // which card
        int i = r.nextInt(2);
        actions += ":play,card"+(i+1);

        String card = i==0?card1:card2;


        for(String cardAction: card.split(":")) // just go ahead and do them in this order
        {
            if(cardAction.startsWith("move")) 
            {
                String guest;
                guest = guestNames[r.nextInt(guestNames.length)];
                piece = pieces.get(guest);
                //moves = getPossibleMoves(piece);
                actions += ":move," + guest + "," + r.nextInt(3) + "," + r.nextInt(4);
            }
            else if(cardAction.startsWith("viewDeck")) 
            {
                actions += ":viewDeck";
            }
            else if(cardAction.startsWith("get")) 
            {
                String gemToGrab;
                if(cardAction.equals("get,")) 
                {
                    // Grab a random gem
                    gemToGrab = this.board.rooms[me.row][me.col].availableGems[r.nextInt(this.board.rooms[me.row][me.col].availableGems.length)];
                    actions += ":get," + gemToGrab;
                }
                else 
                {
                    actions += ":" + cardAction;
                    gemToGrab=cardAction.trim().split(",")[1];
                }
                if(gemToGrab.equals("red")) gemCounts[Suspicion.RED]++;
                else if(gemToGrab.equals("green")) gemCounts[Suspicion.GREEN]++;
                else gemCounts[Suspicion.YELLOW]++;
            }
            else if(cardAction.startsWith("ask"))
            {
                String bestPlayerToAsk = getBestPlayerToAsk(cardAction);
                actions += ":" + cardAction + bestPlayerToAsk;
                // actions += ":" + cardAction + otherPlayerNames[r.nextInt(otherPlayerNames.length)]; 
            }
        }
        return actions;
    }

    void generatePermutations(List<List<String>> lists, List<String> result, int depth, String current) {
        if (depth == lists.size()) {
            result.add(current);
            return;
        }
    
        for (int i = 0; i < lists.get(depth).size(); i++) {
            generatePermutations(lists, result, depth + 1, current + "  " + lists.get(depth).get(i));
        }
    }
    
    private List<Float> getEntropy(List<List<String>> KB) {//get entropy for each players' knowledge bases given 
        ArrayList<String> result = new ArrayList<>();
        List<HashMap<String, Integer>> listCountMap = new ArrayList<HashMap<String, Integer>>();

        generatePermutations(KB, result, 0, "");
        result.removeIf(n -> n.strip().split("  ").length != new HashSet<String>(Arrays.asList(n.strip().split("  "))).size());

        for (int i = 0; i < playerNames.length-1; i++) {
            HashMap<String, Integer> countMap = new HashMap<String, Integer>();
            for (String l: result) {
                String tempFirst = l.strip().split("  ")[i];
                if (countMap.get(tempFirst) == null) {
                    countMap.put(tempFirst, 1);
                } else {
                    countMap.put(tempFirst, countMap.get(tempFirst)+1);
                }
            }
            listCountMap.add(countMap);
        }
        
        List<Float> entropyList = new ArrayList<>();
        for (int j = 0; j < listCountMap.size(); j++) {
            HashMap<String, Integer> l = listCountMap.get(j);
            float entropy = 0;
            for (String m: l.keySet()) {
                entropy += -(float)l.get(m)/result.size() * Math.log((float)l.get(m)/result.size());
            }
            entropyList.add(entropy);
        }
        return entropyList;
    }

    private String getBestPlayerToAsk(String cardAskAction) {
        // remove singleton recursively everytime when it's going to ask other players
        elimSingleton();

        Piece p = pieces.get(cardAskAction.split(",")[1]); //piece we get to ask about
        
        List<List<String>> KB = new ArrayList<List<String>>();
        List<String> indKB = new ArrayList<>();
        
        for (String each: otherPlayerNames) {
            List<String> temp = new ArrayList<>(players.get(each).possibleGuestNames);
            KB.add(temp);
            indKB.add(each);
        }
        
        List<Float> entropyList = getEntropy(KB); //generate overall entropy values for each player
        float highestEntropy = 0;

        List<List<String>> KBY =  new ArrayList<List<String>>();
        List<List<String>> KBN =  new ArrayList<List<String>>();
        
        for (int i = 0; i < KB.size() ; i++) {
            ArrayList<String> py = new ArrayList<String>(players.get(otherPlayerNames[i]).possibleGuestNames);
            ArrayList<String> pn = new ArrayList<String>(players.get(otherPlayerNames[i]).possibleGuestNames);
            for(String k : guestNames) {
                Piece tp = pieces.get(k);
                if (canSee(tp, p)) {
                    pn.remove(tp.name);
                } else {
                    py.remove(tp.name);
                }
            }
            KBY.add(py);
            KBN.add(pn);
        }
        
        List<Float> entropyListY = getEntropy(KBY); //generate entropy for each player's "YES"-answers, should they be asked
        List<Float> entropyListN = getEntropy(KBN); //generate entropy for each player's "NO"-answers, should they be asked

        float highestGain = 0;
        String rval = "";
        for (int i = 0; i < entropyList.size(); i++) { //for each player, determine information gain, store best
            float infoGainY = entropyList.get(i) - entropyListY.get(i);
            float infoGainN = entropyList.get(i) - entropyListN.get(i);

            float PY = ((float)KBY.get(i).size())/(float)KB.get(i).size();
            float PN = ((float)KBN.get(i).size())/(float)KB.get(i).size();
            
            float gain = PY * infoGainY + PN * infoGainN;

            if (gain > highestGain) {
                highestGain = gain;
                rval = indKB.get(i);
            }
        }
        return rval;
    }

    ArrayList<String> singleList = new ArrayList<>();
    int prevCount = 0;
    private void elimSingleton() {
        for (String pname : otherPlayerNames) {
            if (players.get(pname).possibleGuestNames.size() == 1) {
                singleList.add(players.get(pname).possibleGuestNames.get(0));
            } else {
                if (singleList.size() > 0) {
                    for (String sname: singleList) {
                        if (players.get(pname).possibleGuestNames.contains(sname)) {
                            players.get(pname).possibleGuestNames.remove(sname);
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
            for (String pname : otherPlayerNames) {
                if (players.get(pname).possibleGuestNames.contains(sname) && players.get(pname).possibleGuestNames.size() > 1) {
                    players.get(pname).possibleGuestNames.remove(sname);
                }
            }
            singleList.remove(sname);
        }
        elimSingleton();
    }


    private int countGems(String gem)
    {
        if(gem.equals("red")) return gemCounts[Suspicion.RED];
        else if(gem.equals("green")) return gemCounts[Suspicion.GREEN];
        else return gemCounts[Suspicion.YELLOW];
    }

    private ArrayList<String> getGuestsInRoomWithGem(String board, String gemcolor)
    {
        Board b = new Board(board, pieces, gemLocations);
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

    public void reportPlayerActions(String player, String d1, String d2, String cardPlayed, String board[], String actions)
    {
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
                players.get(player).adjustKnowledge(possibleGuests);
            }
        }
    }

    private boolean canSee(Piece p1, Piece p2) // returns whether or not these two pieces see each 
    {
        return (p1.row==p2.row || p1.col == p2.col);
    }

    
    public void answerAsk(String guest, String player, String board, boolean canSee)
    {
        Board b = new Board(board, pieces, gemLocations);
        ArrayList<String> possibleGuests = new ArrayList<String>();
        Piece p1 = pieces.get(guest);  // retrieve the guest 
        for(String k : pieces.keySet())
        {
            Piece p2 = pieces.get(k);
            if((canSee && canSee(p1,p2)) || (!canSee && !canSee(p1,p2))) possibleGuests.add(p2.name);
        }
        players.get(player).adjustKnowledge(possibleGuests);
    }

    public void answerViewDeck(String player)
    {
        for(String k:players.keySet())
        {
            players.get(k).adjustKnowledge(player);
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
        public List<V> variables;
        public Map<V, List<D>> domains;
        public Map<V, List<Constraint<V, D>>> constraints = new HashMap<>();

        public Map<V, List<V>> arcs = new HashMap<>();
        public Map<V, List<V>> queue = new HashMap<>();

        public CSP(List<V> variables, Map<V, List<D>> domains) {
            this.variables = variables;
            this.domains = domains;
            for (V variable: variables) { //list of player strings
               
                constraints.put(variable, new ArrayList<>());
                arcs.put(variable, new ArrayList<>());
                queue.put(variable, new ArrayList<>()); //queue of arcs between 
                for(V varPair: variables) {
                    if(varPair != variable) {arcs.get(variable).add(varPair); queue.get(variable).add(varPair);}
                }
                if (!domains.containsKey(variable)) {
                    throw new IllegalArgumentException("Every variable should havea domain assigned to it.");
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
            //this would be the place to check heuristics and decide what variable to pull
            V minVar = variables.stream().filter(v -> !assignment.containsKey(v)).findFirst().get();
            Iterator<V> it = variables.stream().filter(v -> !assignment.containsKey(v)).iterator();
            while(it.hasNext()) //only unassigned variables
            {
                V temp = it.next();
                if(domains.get(temp).size() < domains.get(minVar).size()) minVar = temp;
            }
            V unassigned = minVar;
           // System.out.println(unassigned.toString());
            D minConflict = domains.get(unassigned).get(0);
            int minConflictCount = Integer.MAX_VALUE;
            for(D value: domains.get(unassigned)) //for each possible guest assignment
            {
                int conflictCount = 0;
                Iterator<V> itD = variables.stream().filter(v -> !assignment.containsKey(v)).iterator();
                while(itD.hasNext()) //only unassigned variables
                {
                    V next = itD.next();
                    if(!(next == unassigned)) 
                    { 
                        if(domains.get(next).contains(value)) conflictCount++;
                    }
                }
                //System.out.println("Unassigned: " + unassigned.toString() + " value " + value.toString() + " conflicts " + conflictCount);
                if(conflictCount < minConflictCount) 
                {
                    minConflictCount = conflictCount;
                    minConflict = value;
                }
            } //should now have the minConflicts value of the domain
            //System.out.println("MinV = " + unassigned.toString() + " ; MinConflictD = " + minConflict.toString());
            Map<V, D> localAssignment = new HashMap<>(assignment);
            localAssignment.put(unassigned, minConflict);
            if (consistent(unassigned, localAssignment)) {
                for(V dDump : domains.keySet()) 
                {
                    if(dDump != unassigned) domains.get(dDump).remove(minConflict); //remove all instances of other guess
                }
                Map<V, D> result = backtrackingSearch(localAssignment);
                if (result != null) {
                    return result;
                }
            }
            else 
                for (D value: domains.get(unassigned)) {
                    //domain-item heuristics should go here
                    Map<V, D> localAssignment2 = new HashMap<>(assignment);
                    localAssignment2.put(unassigned, value);
                    if (consistent(unassigned, localAssignment2)) {
                        for(V dDump : domains.keySet()) 
                        {
                            if(dDump != unassigned) domains.get(dDump).remove(value);
                        }
                        Map<V, D> result = backtrackingSearch(localAssignment2);
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

    /**
     * A method to compute a good looking data structure 
     * where character names are converted into decimal number
     * @return List<List<Integer>> temp_kb
     */
    public List<List<Integer>> getDS() {
        List<List<Integer>> temp_kb = new ArrayList<List<Integer>>();
        for(String k:players.keySet())
        {
            List<Integer> temp = new ArrayList<Integer>();
            for (String p : players.get(k).possibleGuestNames) {
                temp.add(Arrays.asList(guestNames).indexOf(p));
            }
            System.out.println(temp);
            temp_kb.add(temp);
        }
        return temp_kb;
    }
    public List<List<String>> suspicionCSPBacktrack() {
        //implement arc-consistency check here to minimize domains
        //csp.ac3();

        Map<String, String> solution = csp.backtrackingSearch();
        // if (solution == null) {
        //     System.out.println("No solution found!");
        // } else {
        //     System.out.println(solution);
        // }
        
        List<List<String>> csp_kb = new ArrayList<List<String>>();
        for (String k : solution.keySet()) {
            csp_kb.add(Arrays.asList(solution.get(k)));
        }
        
        System.out.println("---Initial KB---");
        for(String k:players.keySet())
        {
            System.out.println(players.get(k).possibleGuestNames);
        }
        return csp_kb;
    }

/* Modify this method to do something more intelligent. */
public String reportGuesses()
{

    List<List<String>> my_kb = suspicionCSPBacktrack();

    String rval="";
    System.out.println("\n---CSP-Infogain Guess---");
    for (int i = 0; i < my_kb.size(); i++) {
        System.out.println(my_kb.get(i));
        rval += players.keySet().toArray()[i];
        //List<Integer> l = my_kb.get(i);
        List<String> l = my_kb.get(i);
        for (String each : l) {
            // rval += "," + guestNames[each];
            rval += "," + each;
        }
        rval += ":";
    }
    System.out.println(rval.substring(0,rval.length()-1));
    return rval.substring(0,rval.length()-1);
}

    public RBotSmartAsk(String playerName, String guestName, int numStartingGems, String gemLocations, String[] playerNames, String[] guestNames)
    {
        super(playerName, guestName, numStartingGems, gemLocations, playerNames, guestNames);
        display = new TextDisplay(gemLocations);
        pieces = new HashMap<String, Piece>();
        ArrayList<String> possibleGuests = new ArrayList<String>();
        for(String name:guestNames)
        {
            pieces.put(name, new Piece(name));
            if(!name.equals(guestName)) possibleGuests.add(name);
        }
        me = pieces.get(guestName);

        players = new HashMap<String, Player>();
        for(String str: playerNames)
        {
            if(!str.equals(playerName)) players.put(str, new Player(str, possibleGuests.toArray(new String[possibleGuests.size()])));
        }

        otherPlayerNames = players.keySet().toArray(new String[players.size()]);

        Integer playerNumber = Integer.parseInt(playerName.substring(playerName.length()-1, playerName.length()));
        variables = new LinkedList<>(Arrays.asList(playerNames));
        variables.remove(playerName); //remove self from CSP

        domains = new HashMap<>();
        
        for (String k:players.keySet()) {
            domains.put(k, players.get(k).possibleGuestNames);
        }
        csp = new CSP<>(variables, domains);

        Integer i = 0;
        for (String k:players.keySet()) {
            String prefix = k.substring(0, k.length()-1);
            for (int j = i+1; j <= players.keySet().size(); j++) {
                if (i != playerNumber && j != playerNumber){
                    csp.addConstraint(new SuspicionConstraint(prefix+i, prefix+j));
                   // csp.addConstraint(new SuspicionConstraint(prefix+j, prefix+i));
                }
            }
            i++;
        } 
    }
}


