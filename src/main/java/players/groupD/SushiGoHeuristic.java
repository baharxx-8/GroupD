package players.groupD;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import games.sushigo.SGGameState;
import games.sushigo.cards.SGCard;
import games.sushigo.cards.SGCard.SGCardType;

import java.util.Map;
import java.util.HashMap;

/**
 * Heuristic evaluation function for the Sushi Go! game.
 * Estimates a player's strength based on their current played cards,
 * remaining hand, and game progress.
 *
 * This function is used both during rollouts and at leaf nodes
 * in the MCTS search tree to guide decision-making.
 */
public class SushiGoHeuristic implements IStateHeuristic {

    @Override
    public double evaluateState(AbstractGameState gameState, int playerId) {
        SGGameState state = (SGGameState) gameState;
        double score = state.getGameScore(playerId);

        // Retrieve counts of all played card types for this player
        Map<SGCardType, Integer> counts = new HashMap<>();
        for (Map.Entry<SGCardType, core.components.Counter> e :
                state.getPlayedCardTypes()[playerId].entrySet()) {
            counts.put(e.getKey(), e.getValue().getValue());
        }

        // --- 1️⃣ TEMPURA (sets of 2 = 5 points) ---
        int tempura = counts.getOrDefault(SGCardType.Tempura, 0);
        // partial set gives small potential bonus
        score += (tempura / 2.0) * 5 + (tempura % 2) * 1.5;

        // --- 2️⃣ SASHIMI (sets of 3 = 10 points) ---
        int sashimi = counts.getOrDefault(SGCardType.Sashimi, 0);
        score += (sashimi / 3.0) * 10 + (sashimi % 3) * 2.5;

        // --- 3️⃣ DUMPLING (increasing returns) ---
        int dumpling = counts.getOrDefault(SGCardType.Dumpling, 0);
        int[] dumplingValues = new int[]{1, 3, 6, 10, 15};
        if (dumpling > 0) {
            score += dumplingValues[Math.min(dumpling, 5) - 1];
        }

        // --- 4️⃣ NIGIRI + WASABI combination ---
        int wasabi = counts.getOrDefault(SGCardType.Wasabi, 0);
        int squid = counts.getOrDefault(SGCardType.SquidNigiri, 0);
        int salmon = counts.getOrDefault(SGCardType.SalmonNigiri, 0);
        int egg = counts.getOrDefault(SGCardType.EggNigiri, 0);
        int totalNigiri = squid + salmon + egg;

        // count how many wasabi can actually be used
        int usedWasabi = Math.min(wasabi, totalNigiri);
        double nigiriScore = squid * 3 + salmon * 2 + egg * 1 + usedWasabi * 2;
        score += nigiriScore;

        // Small penalty for unused wasabi (wasted potential)
        int unusedWasabi = Math.max(0, wasabi - totalNigiri);
        score -= 0.8 * unusedWasabi;

        // --- 5️⃣ MAKI (majority bonus) ---
        int myMaki = counts.getOrDefault(SGCardType.Maki, 0);
        int maxMaki = 0;
        for (int i = 0; i < state.getNPlayers(); i++) {
            if (i != playerId) {
                Map<SGCardType, core.components.Counter> oppCards = state.getPlayedCardTypes()[i];
                int oppMaki = oppCards.containsKey(SGCardType.Maki)
                        ? oppCards.get(SGCardType.Maki).getValue()
                        : 0;
                maxMaki = Math.max(maxMaki, oppMaki);
            }
        }

        if (myMaki > 0) {
            double makiLead = myMaki - maxMaki;
            if (makiLead > 0) score += 6.0;              // leading
            else if (makiLead == 0) score += 3.0;        // tie
            else score += 0.5 * (myMaki / (double) (maxMaki + 1)); // catching up
        }

        // --- 6️⃣ PUDDING (endgame bonus estimate) ---
        int pudding = counts.getOrDefault(SGCardType.Pudding, 0);
        double progress = Math.min(1.0, state.getTurnCounter() / 100.0);
        double endgameWeight = 0.2 + 0.8 * progress; // pudding more valuable late game
        if (pudding > 0)
            score += endgameWeight * (0.8 * pudding);
        else
            score -= endgameWeight * 0.5; // small penalty if none collected

        // --- 7️⃣ CHOPSTICKS (flexibility bonus) ---
        int chopsticks = counts.getOrDefault(SGCardType.Chopsticks, 0);
        score += chopsticks * 1.0;

        // --- 8️⃣ Remaining hand potential ---
        int handSize = state.getPlayerHands().get(playerId).getSize();
        score += handSize * 0.15; // small bonus for still having options

        // --- 9️⃣ Adjust for game progress ---
        score *= (0.9 + 0.2 * progress); // amplifies differences late in the game

        return score;
    }
}
