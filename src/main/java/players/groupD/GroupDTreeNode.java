package players.groupD;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import players.simple.RandomPlayer;
import utilities.ElapsedCpuTimer;

import java.util.*;
import static java.util.stream.Collectors.toList;
import static utilities.Utils.noise;

/**
 * Core implementation of the MCTS search tree node for Group D's Sushi Go! agent.
 * Each node stores visit counts, total value, and child nodes for each possible action.
 */
class GroupDTreeNode {

    // ====== Tree structure ======
    GroupDTreeNode root, parent;
    Map<AbstractAction, GroupDTreeNode> children = new HashMap<>();
    final int depth;

    // ====== Statistics ======
    private double totValue;
    private int nVisits;
    private int fmCallsCount;

    // ====== Shared references ======
    private final AbstractPlayer player;
    private final Random rnd;
    private final RandomPlayer randomPlayer = new RandomPlayer();
    private final AbstractGameState state;

    // ====== Constructor ======
    protected GroupDTreeNode(AbstractPlayer player,
                             GroupDTreeNode parent,
                             AbstractGameState state,
                             Random rnd) {
        this.player = player;
        this.parent = parent;
        this.root = (parent == null) ? this : parent.root;
        this.depth = (parent == null) ? 0 : parent.depth + 1;
        this.totValue = 0.0;
        this.state = state;
        this.rnd = rnd;

        randomPlayer.setForwardModel(player.getForwardModel());

        if (state.isNotTerminal()) {
            for (AbstractAction action :
                    player.getForwardModel().computeAvailableActions(
                            state, player.getParameters().actionSpace)) {
                children.put(action, null);
            }
        }
    }

    // ===================== MCTS CORE =====================

    void mctsSearch() {
        GroupDMCTSParams params = (GroupDMCTSParams) player.getParameters();

        // Time budget setup
        ElapsedCpuTimer timer = new ElapsedCpuTimer();
        timer.setMaxTimeMillis(params.timePerMoveMs);

        int numIters = 0;
        double totalIterMs = 0.0;
        boolean stop = false;

        while (!stop) {
            ElapsedCpuTimer iterTimer = new ElapsedCpuTimer();

            // === Selection + Expansion ===
            GroupDTreeNode selected = treePolicy();

            // === Simulation (Rollout) ===
            double delta = selected.rollOut();

            // === Backpropagation ===
            selected.backUp(delta);

            // === Iteration bookkeeping ===
            numIters++;
            totalIterMs += iterTimer.elapsedMillis();
            double avg = totalIterMs / numIters;

            long remaining = timer.remainingTimeMillis();
            stop = (numIters > params.minIterations) && (remaining <= 2 * avg);
        }
    }

    private GroupDTreeNode treePolicy() {
        GroupDTreeNode cur = this;
        GroupDMCTSParams params = (GroupDMCTSParams) player.getParameters();

        while (cur.state.isNotTerminal() && cur.depth < params.maxTreeDepth) {
            if (!cur.unexpandedActions().isEmpty())
                return cur.expand();

            AbstractAction a = cur.ucb();
            GroupDTreeNode next = cur.children.get(a);

            if (next == null)
                return cur.expand(); // Safety fallback

            cur = next;
        }
        return cur;
    }

    private List<AbstractAction> unexpandedActions() {
        return children.keySet().stream()
                .filter(a -> children.get(a) == null)
                .collect(toList());
    }

    private GroupDTreeNode expand() {
        List<AbstractAction> unchosen = unexpandedActions();
        if (unchosen.isEmpty()) return this;

        AbstractAction chosen = unchosen.get(rnd.nextInt(unchosen.size()));
        AbstractGameState nextState = state.copy();
        advance(nextState, chosen.copy());

        GroupDTreeNode tn = new GroupDTreeNode(player, this, nextState, rnd);
        children.put(chosen, tn);
        return tn;
    }

    private void advance(AbstractGameState gs, AbstractAction act) {
        player.getForwardModel().next(gs, act);
        root.fmCallsCount++; // Track forward model calls (optional)
    }

    // ===================== TREE POLICY (UCB) =====================

    private AbstractAction ucb() {
        AbstractAction best = null;
        double bestVal = -Double.MAX_VALUE;
        GroupDMCTSParams p = (GroupDMCTSParams) player.getParameters();

        for (AbstractAction a : children.keySet()) {
            GroupDTreeNode c = children.get(a);
            if (c == null) continue;

            // Mean value
            double mean = c.totValue / (c.nVisits + p.epsilon);

            // Exploration term
            double explore = p.K * Math.sqrt(Math.log(this.nVisits + 1.0) / (c.nVisits + p.epsilon));

            // ✅ Always maximize from root player's perspective
            double uct = mean + explore;

            // Add small random noise to break ties
            uct = noise(uct, p.epsilon, rnd.nextDouble());

            if (uct > bestVal) {
                best = a;
                bestVal = uct;
            }
        }

        // Safety: choose random if all children are null
        if (best == null && !children.isEmpty()) {
            List<AbstractAction> acts = new ArrayList<>(children.keySet());
            best = acts.get(rnd.nextInt(acts.size()));
        }

        root.fmCallsCount++;
        return best;
    }

    // ================== HEURISTIC-GUIDED ROLLOUT ==================

    private double rollOut() {
        int depth = 0;
        AbstractGameState rollState = state.copy();
        GroupDMCTSParams params = (GroupDMCTSParams) player.getParameters();
        double epsilonGreedy = params.epsilonGreedy;

        // Rollout loop
        while (!finishRollout(rollState, depth, params)) {
            List<AbstractAction> actions = player.getForwardModel()
                    .computeAvailableActions(rollState, player.getParameters().actionSpace);

            if (actions == null || actions.isEmpty()) break;

            AbstractAction bestAction = null;
            double bestScore = -Double.MAX_VALUE;

            // ε-greedy selection: explore randomly with some probability
            if (rnd.nextDouble() < epsilonGreedy) {
                bestAction = actions.get(rnd.nextInt(actions.size()));
            } else {
                for (AbstractAction a : actions) {
                    AbstractGameState sim = rollState.copy();
                    advance(sim, a.copy());

                    double score = params.getStateHeuristic().evaluateState(sim, player.getPlayerID());
                    score += rnd.nextGaussian() * 0.005; // Small noise

                    if (score > bestScore) {
                        bestScore = score;
                        bestAction = a;
                    }
                }
            }

            if (bestAction == null)
                bestAction = actions.get(rnd.nextInt(actions.size()));

            advance(rollState, bestAction.copy());
            depth++;

            // Safety cutoff for excessive depth
            if (depth > params.rolloutLength * 2) break;
        }

        double value = params.getStateHeuristic().evaluateState(rollState, player.getPlayerID());
        if (Double.isNaN(value))
            throw new AssertionError("Heuristic returned NaN value.");
        return value;
    }

    private boolean finishRollout(AbstractGameState s, int depth, GroupDMCTSParams params) {
        return depth >= params.rolloutLength || !s.isNotTerminal();

    }

    // ====================== BACKUP & BEST ACTION ======================

    private void backUp(double result) {
        GroupDTreeNode n = this;
        while (n != null) {
            n.nVisits++;
            // Optional: normalize result if heuristic has wide range
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

            // Choose the most visited child (with noise to break ties)
            double val = noise(n.nVisits, params.epsilon, rnd.nextDouble());
            if (val > bestVal) {
                bestVal = val;
                best = a;
            }
        }

        // Safety fallback
        if (best == null && !children.isEmpty()) {
            List<AbstractAction> acts = new ArrayList<>(children.keySet());
            best = acts.get(rnd.nextInt(acts.size()));
        }
        return best;
    }
}
