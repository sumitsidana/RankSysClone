/* 
 * Copyright (C) 2015 Information Retrieval Group at Universidad Autónoma
 * de Madrid, http://ir.ii.uam.es
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package es.uam.eps.ir.ranksys.examples;

import cc.mallet.topics.ParallelTopicModel;
import es.uam.eps.ir.ranksys.core.feature.FeatureData;
import es.uam.eps.ir.ranksys.core.feature.SimpleFeatureData;
import es.uam.eps.ir.ranksys.fast.index.FastItemIndex;
import es.uam.eps.ir.ranksys.fast.index.FastUserIndex;
import es.uam.eps.ir.ranksys.fast.index.SimpleFastItemIndex;
import es.uam.eps.ir.ranksys.fast.index.SimpleFastUserIndex;
import es.uam.eps.ir.ranksys.fast.preference.FastPreferenceData;
import es.uam.eps.ir.ranksys.fast.preference.SimpleFastPreferenceData;
import es.uam.eps.ir.ranksys.mf.Factorization;
import es.uam.eps.ir.ranksys.mf.als.HKVFactorizer;
import es.uam.eps.ir.ranksys.mf.als.PZTFactorizer;
import es.uam.eps.ir.ranksys.mf.plsa.PLSAFactorizer;
import es.uam.eps.ir.ranksys.mf.rec.MFRecommender;
import es.uam.eps.ir.ranksys.nn.item.ItemNeighborhoodRecommender;
import es.uam.eps.ir.ranksys.nn.item.neighborhood.ItemNeighborhood;
import es.uam.eps.ir.ranksys.nn.item.neighborhood.ItemNeighborhoods;
import es.uam.eps.ir.ranksys.nn.item.sim.ItemSimilarities;
import es.uam.eps.ir.ranksys.nn.item.sim.ItemSimilarity;
import es.uam.eps.ir.ranksys.nn.user.UserNeighborhoodRecommender;
import es.uam.eps.ir.ranksys.nn.user.neighborhood.UserNeighborhood;
import es.uam.eps.ir.ranksys.nn.user.neighborhood.UserNeighborhoods;
import es.uam.eps.ir.ranksys.nn.user.sim.UserSimilarities;
import es.uam.eps.ir.ranksys.nn.user.sim.UserSimilarity;
import es.uam.eps.ir.ranksys.novdiv.distance.CosineFeatureItemDistanceModel;
import es.uam.eps.ir.ranksys.novdiv.distance.ItemDistanceModel;
import es.uam.eps.ir.ranksys.rec.Recommender;
import es.uam.eps.ir.ranksys.rec.fast.basic.PopularityRecommender;
import es.uam.eps.ir.ranksys.rec.fast.basic.RandomRecommender;
import es.uam.eps.ir.ranksys.rec.runner.RecommenderRunner;
import es.uam.eps.ir.ranksys.rec.runner.fast.FastFilterRecommenderRunner;
import es.uam.eps.ir.ranksys.rec.runner.fast.FastFilters;

import org.insightcentre.ranksys.divmf.DivHKVFactorizer;
import org.jooq.lambda.Unchecked;
import org.ranksys.fm.PreferenceFM;
import org.ranksys.fm.learner.BPRLearner;
import org.ranksys.fm.learner.RMSELearner;
import org.ranksys.fm.rec.FMRecommender;
import org.ranksys.formats.feature.SimpleFeaturesReader;
import org.ranksys.formats.index.ItemsReader;
import org.ranksys.formats.index.UsersReader;
import org.ranksys.formats.preference.SimpleRatingPreferencesReader;
import org.ranksys.formats.rec.RecommendationFormat;
import org.ranksys.formats.rec.SimpleRecommendationFormat;
import org.ranksys.lda.LDAModelEstimator;
import org.ranksys.lda.LDARecommender;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.ranksys.formats.parsing.Parsers.lp;
import static org.ranksys.formats.parsing.Parsers.sp;

/**
 * Example main of recommendations.
 *
 * @author Saúl Vargas (saul.vargas@uam.es)
 * @author Pablo Castells (pablo.castells@uam.es)
 */
public class RecommenderExample {

	public static void main(String[] args) throws IOException {
		String userPath = args[0];
		String itemPath = args[1];
		String trainDataPath = args[2];
		String testDataPath = args[3];
		String featurePath = args[4];

		FastUserIndex<Long> userIndex = SimpleFastUserIndex.load(UsersReader.read(userPath, lp));
		FastItemIndex<Long> itemIndex = SimpleFastItemIndex.load(ItemsReader.read(itemPath, lp));
		FastPreferenceData<Long, Long> trainData = SimpleFastPreferenceData.load(SimpleRatingPreferencesReader.get().read(trainDataPath, lp, lp), userIndex, itemIndex);
		FastPreferenceData<Long, Long> testData = SimpleFastPreferenceData.load(SimpleRatingPreferencesReader.get().read(testDataPath, lp, lp), userIndex, itemIndex);
		FeatureData<Long, String, Double> featureData = SimpleFeatureData.load(SimpleFeaturesReader.get().read(featurePath, lp, sp));

		ItemDistanceModel<Long> dist = new CosineFeatureItemDistanceModel<>(featureData);

		//////////////////
		// RECOMMENDERS //
		//////////////////
		Map<String, Supplier<Recommender<Long, Long>>> recMap = new HashMap<>();

		// random recommendation
		recMap.put("rnd", () -> new RandomRecommender<>(trainData, trainData));

		// most-popular recommendation
		recMap.put("pop", () -> new PopularityRecommender<>(trainData));

		// user-based nearest neighbors
		recMap.put("ub", () -> {
			int k = 100;
			int q = 1;

			UserSimilarity<Long> sim = UserSimilarities.vectorCosine(trainData, true);
			UserNeighborhood<Long> neighborhood = UserNeighborhoods.topK(sim, k);

			return new UserNeighborhoodRecommender<>(trainData, neighborhood, q);
		});

		// item-based nearest neighbors
		recMap.put("ib", () -> {
			int k = 10;
			int q = 1;

			ItemSimilarity<Long> sim = ItemSimilarities.vectorCosine(trainData, true);
			ItemNeighborhood<Long> neighborhood = ItemNeighborhoods.cached(ItemNeighborhoods.topK(sim, k));

			return new ItemNeighborhoodRecommender<>(trainData, neighborhood, q);
		});

		// implicit matrix factorization of Hu et al. 2008
		recMap.put("hkv", () -> {
			int k = 50;
			double lambda = 0.1;
			double alpha = 1.0;
			DoubleUnaryOperator confidence = x -> 1 + alpha * x;
			int numIter = 20;

			Factorization<Long, Long> factorization = new HKVFactorizer<Long, Long>(lambda, confidence, numIter).factorize(k, trainData);

			return new MFRecommender<>(userIndex, itemIndex, factorization);
		});

		// implicit matrix factorization with diversity regularizer
		recMap.put("divhkv", () -> {
			int k = 50;
			double lambda = 0.1;
			double alpha = 1.0;
			DoubleUnaryOperator confidence = x -> 1 + alpha * x;
			int numIter = 20;
			Factorization<Long, Long> divfactorization = new DivHKVFactorizer<Long, Long>(lambda, alpha, dist, confidence, numIter).factorize(k, trainData);
			return new MFRecommender<>(userIndex, itemIndex, divfactorization);
		});

		// implicit matrix factorization of Pilaszy et al. 2010
		recMap.put("pzt", () -> {
			int k = 50;
			double lambda = 0.1;
			double alpha = 1.0;
			DoubleUnaryOperator confidence = x -> 1 + alpha * x;
			int numIter = 20;

			Factorization<Long, Long> factorization = new PZTFactorizer<Long, Long>(lambda, confidence, numIter).factorize(k, trainData);

			return new MFRecommender<>(userIndex, itemIndex, factorization);
		});

		// probabilistic latent semantic analysis of Hofmann 2004
		recMap.put("plsa", () -> {
			int k = 50;
			int numIter = 100;

			Factorization<Long, Long> factorization = new PLSAFactorizer<Long, Long>(numIter).factorize(k, trainData);

			return new MFRecommender<>(userIndex, itemIndex, factorization);
		});

		// LDA topic modelling by Blei et al. 2003
		recMap.put("lda", Unchecked.supplier(() -> {
			int k = 50;
			double alpha = 1.0;
			double beta = 0.01;
			int numIter = 200;
			int burninPeriod = 50;

			ParallelTopicModel topicModel = LDAModelEstimator.estimate(trainData, k, alpha, beta, numIter, burninPeriod);

			return new LDARecommender<>(userIndex, itemIndex, topicModel);
		}));

		// Factorisation machine using a BRP-like loss
		recMap.put("fm-bpr", Unchecked.supplier(() -> {

			double learnRate = 0.01;
			int numIter = 200;
			double regW = 0.01;
			double regM = 0.01;
			int K = 100;
			double sdev = 0.1;

			PreferenceFM<Long, Long> prefFm = new BPRLearner<>(learnRate, numIter, regW, regM, userIndex, itemIndex).learn(trainData, testData, K, sdev);

			return new FMRecommender<>(prefFm);
		}));

		// Factorisation machine usinga RMSE-like loss with balanced sampling of negative
		// instances
		recMap.put("fm-rmse", Unchecked.supplier(() -> {

			double learnRate = 0.01;
			int numIter = 50;
			double regB = 0.01;
			double regW = 0.01;
			double regM = 0.01;
			double negativeProp = 2.0;
			int K = 100;
			double sdev = 0.1;

			PreferenceFM<Long, Long> prefFm = new RMSELearner<>(learnRate, numIter, regB, regW, regM, negativeProp, userIndex, itemIndex).learn(trainData, testData, K, sdev);

			return new FMRecommender<>(prefFm);
		}));

		////////////////////////////////
		// GENERATING RECOMMENDATIONS //
		////////////////////////////////
		Set<Long> targetUsers = testData.getUsersWithPreferences().collect(Collectors.toSet());
		RecommendationFormat<Long, Long> format = new SimpleRecommendationFormat<>(lp, lp);
		Function<Long, IntPredicate> filter = FastFilters.notInTrain(trainData);
		int maxLength = 100;
		RecommenderRunner<Long, Long> runner = new FastFilterRecommenderRunner<>(userIndex, itemIndex, targetUsers.stream(), filter, maxLength);

		recMap.forEach(Unchecked.biConsumer((name, recommender) -> {
			System.out.println("Running " + name);
			try (RecommendationFormat.Writer<Long, Long> writer = format.getWriter(name)) {
				runner.run(recommender.get(), writer);
			}
		}));
	}
}
