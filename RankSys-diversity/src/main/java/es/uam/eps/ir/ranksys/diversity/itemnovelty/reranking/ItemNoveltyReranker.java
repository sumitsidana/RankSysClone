/* 
 * Copyright (C) 2014 Information Retrieval Group at Universidad Autonoma de Madrid, http://ir.ii.uam.es
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package es.uam.eps.ir.ranksys.diversity.itemnovelty.reranking;

import es.uam.eps.ir.ranksys.core.IdDoublePair;
import es.uam.eps.ir.ranksys.core.Recommendation;
import es.uam.eps.ir.ranksys.diversity.itemnovelty.ItemNovelty;
import es.uam.eps.ir.ranksys.diversity.reranking.PermutationReranker;
import es.uam.eps.ir.ranksys.core.util.structs.IntDoubleTopN;
import es.uam.eps.ir.ranksys.core.util.structs.ObjectDoubleTopN;
import es.uam.eps.ir.ranksys.core.util.Stats;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 *
 * @author Saúl Vargas (saul.vargas@uam.es)
 * @author Pablo Castells (pablo.castells@uam.es)
 */
public class ItemNoveltyReranker<U, I> extends PermutationReranker<U, I> {

    private final double lambda;
    private final ItemNovelty<U, I> novelty;
    private final int cutoff;

        
    public ItemNoveltyReranker(double lambda, ItemNovelty<U, I> novelty) {
        this(lambda, novelty, 0);
    }

    public ItemNoveltyReranker(double lambda, ItemNovelty<U, I> novelty, int cutoff) {
        this.lambda = lambda;
        this.novelty = novelty;
        this.cutoff = cutoff;
    }
    
    protected Stats getRelStats(Recommendation<U, I> recommendation) {
        Stats relStats = new Stats();
        recommendation.getItems().stream().forEach((iv) -> {
            relStats.increment(iv.v);
        });
        
        return relStats;
    }
    
    protected Pair<Stats, TObjectDoubleMap<I>> getNovInfo(Recommendation<U, I> recommendation) {
        TObjectDoubleMap<I> novMap = new TObjectDoubleHashMap<>();
        Stats novStats = new Stats();
        U u = recommendation.getUser();
        recommendation.getItems().stream().forEach((iv) -> {
            double nov = novelty.novelty(iv.id, u);
            novMap.put(iv.id, nov);
            novStats.increment(nov);
        });

        return Pair.of(novStats, novMap);
    }
    
    protected double norm(double score, Stats stats) {
        return (score - stats.getMean()) / stats.getStandardDeviation();
    }
    
    protected double score(double rel, Stats relStats, double nov, Stats novStats) {
        return (1 - lambda) * norm(rel, relStats) + lambda * norm(nov, novStats);
    }

    @Override
    public Recommendation<U, I> rerankRecommendation(Recommendation<U, I> recommendation) {
        int N = cutoff;
        if (cutoff == 0) {
            N = recommendation.getItems().size();
        }
        
        if (lambda == 0.0) {
            List<IdDoublePair<I>> items = recommendation.getItems();
            items = items.subList(0, Math.min(N, items.size()));
            
            return new Recommendation<>(recommendation.getUser(), items);
        }

        U u = recommendation.getUser();
        Stats relStats = getRelStats(recommendation);
        Pair<Stats, TObjectDoubleMap<I>> novInfo = getNovInfo(recommendation);
        
        ObjectDoubleTopN<I> topN = new ObjectDoubleTopN<>(N);
        recommendation.getItems().stream().forEach(is -> topN.add(is.id, score(is.v, relStats, novInfo.getRight().get(is.id), novInfo.getLeft())));
        topN.sort();

        List<IdDoublePair<I>> items = new ArrayList<>();
        for (int i = topN.size() - 1; i >= 0; i--) {
            items.add(new IdDoublePair<>(topN.getKeyAt(i), topN.getValueAt(i)));
        }

        return new Recommendation<>(recommendation.getUser(), items);
    }

    @Override
    public int[] rerankPermutation(Recommendation<U, I> recommendation) {
        int N = cutoff;
        if (cutoff == 0) {
            N = recommendation.getItems().size();
        }
        
        if (lambda == 0.0) {
            return getBasePerm(Math.min(N, recommendation.getItems().size()));
        }

        U u = recommendation.getUser();
        Stats relStats = getRelStats(recommendation);
        Pair<Stats, TObjectDoubleMap<I>> novInfo = getNovInfo(recommendation);
        
        IntDoubleTopN topN = new IntDoubleTopN(N);
        List<IdDoublePair<I>> list = recommendation.getItems();
        for (int i = 0; i < list.size(); i++) {
            topN.add(i, score(list.get(i).v, relStats, novInfo.getRight().get(list.get(i).id), novInfo.getLeft()));
        }
        topN.sort();

        int[] perm = new int[topN.size()];
        for (int i = 0; i < topN.size(); i++) {
            perm[i] = topN.getKeyAt(topN.size() - i - 1);
        }
        
        return perm;
    }

}