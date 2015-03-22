/* 
 * Copyright (C) 2015 Information Retrieval Group at Universidad Autonoma
 * de Madrid, http://ir.ii.uam.es
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
package es.uam.eps.ir.ranksys.metrics.rel;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Relevance model in which there is full, a-priori knowledge of the relevance
 * of all the items in the collection.
 *
 * @author Saúl Vargas (saul.vargas@uam.es)
 * @author Pablo Castells (pablo.castells@uam.es)
 * 
 * @param <U> type of the users
 * @param <I> type of the items
 */
public abstract class IdealRelevanceModel<U, I> extends RelevanceModel<U, I> {

    /**
     * Full constructor: allows to specify whether to cache the user
     * relevance models and for which users.
     *
     * @param caching are the user relevance models cached?
     * @param users users whose relevance models are cached
     */
    public IdealRelevanceModel(boolean caching, Stream<U> users) {
        super(caching, users);
    }

    /**
     * No caching constructor.
     */
    public IdealRelevanceModel() {
        super();
    }

    /**
     * Caching constructor.
     *
     * @param users users whose relevance models are cached
     */
    public IdealRelevanceModel(Stream<U> users) {
        super(users);
    }

    @Override
    protected abstract UserIdealRelevanceModel<U, I> get(U user);

    @Override
    public UserIdealRelevanceModel<U, I> getModel(U user) {
        return (UserIdealRelevanceModel<U, I>) super.getModel(user);
    }

    /**
     * User relevance model for IdealRelevanceModel
     *
     * @param <U> type of the users
     * @param <I> type of the item
     */
    public interface UserIdealRelevanceModel<U, I> extends UserRelevanceModel<U, I> {

        /**
         * Obtains all the items relevant to the user.
         *
         * @return set of items relevant to the user
         */
        public Set<I> getRelevantItems();

    }
}
