/*
 * Copyright 2016-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.ldap.repository.support;

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.ldap.core.mapping.LdapMappingContext;
import org.springframework.data.ldap.repository.query.DtoInstantiatingConverter;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.EntityInstantiators;
import org.springframework.data.projection.ProjectionInformation;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.lang.Nullable;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.odm.core.ObjectDirectoryMapper;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.util.Assert;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;

/**
 * Base repository implementation for QueryDSL support.
 *
 * @author Mattias Hellborg Arthursson
 * @author Eddu Melendez
 * @author Mark Paluch
 */
public class QuerydslLdapRepository<T> extends SimpleLdapRepository<T>
		implements QuerydslPredicateExecutor<T>, BeanFactoryAware, BeanClassLoaderAware {

	private final LdapOperations ldapOperations;
	private final MappingContext<? extends PersistentEntity<?, ?>, ? extends PersistentProperty<?>> context;
	private final Class<T> entityType;
	private final SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
	private final EntityInstantiators entityInstantiators = new EntityInstantiators();

	/**
	 * Creates a new {@link QuerydslLdapRepository}.
	 *
	 * @param ldapOperations must not be {@literal null}.
	 * @param odm must not be {@literal null}.
	 * @param entityType must not be {@literal null}.
	 */
	public QuerydslLdapRepository(LdapOperations ldapOperations, ObjectDirectoryMapper odm, Class<T> entityType) {

		super(ldapOperations, odm, entityType);

		this.ldapOperations = ldapOperations;
		this.entityType = entityType;
		this.context = new LdapMappingContext();
	}

	/**
	 * Creates a new {@link QuerydslLdapRepository}.
	 *
	 * @param ldapOperations must not be {@literal null}.
	 * @param context must not be {@literal null}.
	 * @param odm must not be {@literal null}.
	 * @param entityType must not be {@literal null}.
	 * @since 2.6
	 */
	QuerydslLdapRepository(LdapOperations ldapOperations,
			MappingContext<? extends PersistentEntity<?, ?>, ? extends PersistentProperty<?>> context,
			ObjectDirectoryMapper odm, Class<T> entityType) {

		super(ldapOperations, context, odm, entityType);

		this.ldapOperations = ldapOperations;
		this.context = context;
		this.entityType = entityType;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		projectionFactory.setBeanClassLoader(classLoader);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		projectionFactory.setBeanFactory(beanFactory);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.querydsl.QueryDslPredicateExecutor#findOne(com.querydsl.core.types.Predicate)
	 */
	@Override
	public Optional<T> findOne(Predicate predicate) {
		return findBy(predicate, Function.identity()).one();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.querydsl.QueryDslPredicateExecutor#findAll(com.querydsl.core.types.Predicate)
	 */
	@Override
	public List<T> findAll(Predicate predicate) {
		return queryFor(predicate).list();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.querydsl.QueryDslPredicateExecutor#count(com.querydsl.core.types.Predicate)
	 */
	@Override
	public long count(Predicate predicate) {
		return findAll(predicate).size();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.querydsl.QueryDslPredicateExecutor#exists(com.querydsl.core.types.Predicate)
	 */
	public boolean exists(Predicate predicate) {
		return count(predicate) > 0;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.querydsl.QueryDslPredicateExecutor#findAll(com.querydsl.core.types.Predicate, org.springframework.data.domain.Sort)
	 */
	public Iterable<T> findAll(Predicate predicate, Sort sort) {
		throw new UnsupportedOperationException();
	}


	/* (non-Javadoc)
	 * @see org.springframework.data.querydsl.QueryDslPredicateExecutor#findAll(com.querydsl.core.types.OrderSpecifier[])
	 */
	public Iterable<T> findAll(OrderSpecifier<?>... orders) {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.querydsl.QueryDslPredicateExecutor#findAll(com.querydsl.core.types.Predicate, com.querydsl.core.types.OrderSpecifier[])
	 */
	@Override
	public Iterable<T> findAll(Predicate predicate, OrderSpecifier<?>... orders) {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.querydsl.QueryDslPredicateExecutor#findAll(com.querydsl.core.types.Predicate, org.springframework.data.domain.Pageable)
	 */
	@Override
	public Page<T> findAll(Predicate predicate, Pageable pageable) {
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.querydsl.QuerydslPredicateExecutor#findBy(com.querydsl.core.types.Predicate, java.util.function.Function)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <S extends T, R> R findBy(Predicate predicate,
			Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {

		Assert.notNull(predicate, "Predicate must not be null!");
		Assert.notNull(queryFunction, "Query function must not be null!");

		return queryFunction.apply(new FluentQuerydsl<>(predicate, (Class<S>) entityType));
	}

	private QuerydslLdapQuery<T> queryFor(Predicate predicate) {
		return queryFor(predicate, it -> {

		});
	}

	private QuerydslLdapQuery<T> queryFor(Predicate predicate, Consumer<LdapQueryBuilder> queryBuilderConsumer) {
		return new QuerydslLdapQuery<>(ldapOperations, entityType, queryBuilderConsumer).where(predicate);
	}

	/**
	 * {@link org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery} using {@link Example}.
	 *
	 * @author Mark Paluch
	 * @since 2.6
	 */
	class FluentQuerydsl<R> implements FluentQuery.FetchableFluentQuery<R> {

		private final Predicate predicate;
		private final Sort sort;
		private final Class<R> resultType;
		private final List<String> projection;

		FluentQuerydsl(Predicate predicate, Class<R> resultType) {
			this(predicate, Sort.unsorted(), resultType, Collections.emptyList());
		}

		FluentQuerydsl(Predicate predicate, Sort sort, Class<R> resultType, List<String> projection) {
			this.predicate = predicate;
			this.sort = sort;
			this.resultType = resultType;
			this.projection = projection;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery#sortBy(org.springframework.data.domain.Sort)
		 */
		@Override
		public FetchableFluentQuery<R> sortBy(Sort sort) {
			throw new UnsupportedOperationException();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery#as(java.lang.Class)
		 */
		@Override
		public <R1> FetchableFluentQuery<R1> as(Class<R1> resultType) {

			Assert.notNull(projection, "Projection target type must not be null!");

			return new FluentQuerydsl<>(predicate, sort, resultType, projection);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery#project(java.util.Collection)
		 */
		@Override
		public FetchableFluentQuery<R> project(Collection<String> properties) {

			Assert.notNull(properties, "Projection properties must not be null!");

			return new FluentQuerydsl<>(predicate, sort, resultType, new ArrayList<>(properties));
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery#oneValue()
		 */
		@Nullable
		@Override
		public R oneValue() {

			List<T> results = findTop(2);

			if (results.isEmpty()) {
				return null;
			}

			if (results.size() > 1) {
				throw new IncorrectResultSizeDataAccessException(1);
			}

			T one = results.get(0);
			return getConversionFunction(entityType, resultType).apply(one);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery#firstValue()
		 */
		@Nullable
		@Override
		public R firstValue() {

			List<T> results = findTop(2);

			if (results.isEmpty()) {
				return null;
			}

			T one = results.get(0);
			return getConversionFunction(entityType, resultType).apply(one);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery#all()
		 */
		@Override
		public List<R> all() {
			return stream().collect(Collectors.toList());
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery#page(org.springframework.data.domain.Pageable)
		 */
		@Override
		public Page<R> page(Pageable pageable) {

			Assert.notNull(pageable, "Pageable must not be null!");
			throw new UnsupportedOperationException();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery#stream()
		 */
		@Override
		public Stream<R> stream() {

			Function<Object, R> conversionFunction = getConversionFunction(entityType, resultType);

			return search(null, QuerydslLdapQuery::list).stream().map(conversionFunction);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery#count()
		 */
		@Override
		public long count() {
			return search(null, q -> q.search(it -> true)).size();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery#exists()
		 */
		@Override
		public boolean exists() {
			return !search(1, q -> q.search(it -> true)).isEmpty();
		}

		private List<T> findTop(int limit) {
			return search(limit, QuerydslLdapQuery::list);
		}

		private <S> S search(@Nullable Integer limit, Function<QuerydslLdapQuery<T>, S> searchFunction) {

			QuerydslLdapQuery<T> q = queryFor(predicate, query -> {

				List<String> projection = getProjection();

				if (!projection.isEmpty()) {
					query.attributes(projection.toArray(new String[0]));
				}

				if (limit != null) {
					query.countLimit(limit);
				}
			});

			return searchFunction.apply(q);
		}

		@SuppressWarnings("unchecked")
		private <P> Function<Object, P> getConversionFunction(Class<?> inputType, Class<P> targetType) {

			if (targetType.isAssignableFrom(inputType)) {
				return (Function<Object, P>) Function.identity();
			}

			if (targetType.isInterface()) {
				return o -> projectionFactory.createProjection(targetType, o);
			}

			DtoInstantiatingConverter converter = new DtoInstantiatingConverter(targetType, context, entityInstantiators);

			return o -> (P) converter.convert(o);
		}

		private List<String> getProjection() {

			if (projection.isEmpty()) {

				if (resultType.isAssignableFrom(entityType)) {
					return projection;
				}

				if (resultType.isInterface()) {
					ProjectionInformation projectionInformation = projectionFactory.getProjectionInformation(resultType);

					if (projectionInformation.isClosed()) {
						return projectionInformation.getInputProperties().stream().map(FeatureDescriptor::getName)
								.collect(Collectors.toList());
					}
				}
			}

			return projection;
		}
	}
}
