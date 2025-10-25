package players.groupD;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import players.PlayerConstants;
import players.simple.RandomPlayer;
import utilities.ElapsedCpuTimer;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static players.PlayerConstants.*;
import static utilities.Utils.noise;

class GroupDTreeNode {

    GroupDTreeNode root, parent;
    Map<AbstractAction, GroupDTreeNode> children = new HashMap<>();
    final int depth;
    private double totValue;
    private int nVisits;
    private int fmCallsCount;
    private AbstractPlayer player;      // ✅ FIX: use AbstractPlayer
    private Random rnd;
    private final RandomPlayer randomPlayer = new RandomPlayer();
    private final AbstractGameState state;

    protected GroupDTreeNode(AbstractPlayer player, GroupDTreeNode parent,
                             AbstractGameState state, Random rnd) {
        this.player = player;
        this.parent = parent;
        this.root = parent == null ? this : parent.root;
        this.depth = parent == null ? 0 : parent.depth + 1;
        this.totValue = 0.0;
        this.state = state;
        this.rnd = rnd;

        // initialize random rollout policy
        randomPlayer.setForwardModel(player.getForwardModel());

        // add available actions to this node
        if (state.isNotTerminal()) {
            for (AbstractAction action :
                    player.getForwardModel().computeAvailableActions(state,
                            player.getParameters().actionSpace)) {
                children.put(action, null);
            }
        }
    }

    // ---------- MCTS Core ----------

    void mctsSearch() {
        GroupDMCTSParams params = (GroupDMCTSParams) player.getParameters();
        ElapsedCpuTimer timer = new ElapsedCpuTimer();
        timer.setMaxTimeMillis(params.budget);
        int numIters = 0;
        double avg = 0, total = 0;
        boolean stop = false;

        while (!stop) {
            ElapsedCpuTimer iterTimer = new ElapsedCpuTimer();

            GroupDTreeNode selected = treePolicy();
            double delta = selected.rollOut();
            selected.backUp(delta);
            numIters++;

            total += iterTimer.elapsedMillis();
            avg = total / numIters;
            long remaining = timer.remainingTimeMillis();
            stop = remaining <= 2 * avg || remaining <= params.breakMS;
        }
    }

    private GroupDTreeNode treePolicy() {
        GroupDTreeNode cur = this;
        GroupDMCTSParams params = (GroupDMCTSParams) player.getParameters();

        while (cur.state.isNotTerminal() && cur.depth < params.maxTreeDepth) {
            if (!cur.unexpandedActions().isEmpty()) return cur.expand();
            AbstractAction a = cur.ucb();
            cur = cur.children.get(a);
        }
        return cur;
    }

    private List<AbstractAction> unexpandedActions() {
        return children.keySet().stream().filter(a -> children.get(a) == null).collect(toList());
    }

    private GroupDTreeNode expand() {
        Random r = new Random(player.getParameters().getRandomSeed());
        List<AbstractAction> unchosen = unexpandedActions();
        AbstractAction chosen = unchosen.get(r.nextInt(unchosen.size()));
        AbstractGameState nextState = state.copy();
        advance(nextState, chosen.copy());
        GroupDTreeNode tn = new GroupDTreeNode(player, this, nextState, rnd);
        children.put(chosen, tn);
        return tn;
    }

    private void advance(AbstractGameState gs, AbstractAction act) {
        player.getForwardModel().next(gs, act);
        root.fmCallsCount++;
    }

    private AbstractAction ucb() {
        AbstractAction best = null;
        double bestVal = -Double.MAX_VALUE;
        GroupDMCTSParams p = (GroupDMCTSParams) player.getParameters();

        for (AbstractAction a : children.keySet()) {
            GroupDTreeNode c = children.get(a);
            if (c == null) continue;

            double val = c.totValue / (c.nVisits + p.epsilon);
            double explore = p.K * Math.sqrt(Math.log(this.nVisits + 1) / (c.nVisits + p.epsilon));
            boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
            double uct = (iAmMoving ? val : -val) + explore;
            uct = noise(uct, p.epsilon, player.getRnd().nextDouble());

            if (uct > bestVal) {
                best = a;
                bestVal = uct;
            }
        }
        root.fmCallsCount++;
        return best;
    }

    private double rollOut() {
        int depth = 0;
        AbstractGameState rollState = state.copy();
        GroupDMCTSParams params = (GroupDMCTSParams) player.getParameters();

        while (!finishRollout(rollState, depth, params)) {
            AbstractAction next = randomPlayer.getAction(
                    rollState,
                    randomPlayer.getForwardModel().computeAvailableActions(
                            rollState, randomPlayer.parameters.actionSpace));
            advance(rollState, next);
            depth++;
        }

        double value = params.getStateHeuristic().evaluateState(rollState, player.getPlayerID());
        if (Double.isNaN(value))
            throw new AssertionError("Heuristic returned NaN value.");
        return value;
    }

    private boolean finishRollout(AbstractGameState s, int depth, GroupDMCTSParams params) {
        return depth >= params.rolloutLength || !s.isNotTerminal();
    }

    private void backUp(double result) {
        GroupDTreeNode n = this;
        while (n != null) {
            n.nVisits++;
            n.totValue += result;
            n = n.parent;
        }
    }

    AbstractAction bestAction() {
        double bestVal = -Double.MAX_VALUE;
        AbstractAction best = null;
        GroupDMCTSParams params = (GroupDMCTSParams) player.getParameters();

        for (AbstractAction a : children.keySet()) {
            GroupDTreeNode n = children.get(a);
            if (n == null) continue;
            double val = noise(n.nVisits, params.epsilon, player.getRnd().nextDouble());
            if (val > bestVal) {
                bestVal = val;
                best = a;
            }
        }
        return best;
    }
}
