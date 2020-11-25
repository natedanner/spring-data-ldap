/*
 * Copyright 2016-2020 the original author or authors.
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

import static org.springframework.ldap.query.LdapQueryBuilder.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.naming.Name;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Persistable;
import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.data.util.Optionals;
import org.springframework.lang.Nullable;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.core.support.CountNameClassPairCallbackHandler;
import org.springframework.ldap.filter.Filter;
import org.springframework.ldap.odm.core.ObjectDirectoryMapper;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.util.Assert;

/**
 * Base repository implementation for LDAP.
 *
 * @author Mattias Hellborg Arthursson
 * @author Mark Paluch
 * @author Jens Schauder
 */
public class SimpleLdapRepository<T> implements LdapRepository<T> {

	private static final String OBJECTCLASS_ATTRIBUTE = "objectclass";

	private final LdapOperations ldapOperations;
	private final ObjectDirectoryMapper odm;
	private final Class<T> entityType;

	/**
	 * Creates a new {@link SimpleLdapRepository}.
	 *
	 * @param ldapOperations must not be {@literal null}.
	 * @param odm must not be {@literal null}.
	 * @param entityType must not be {@literal null}.
	 */
	public SimpleLdapRepository(LdapOperations ldapOperations, ObjectDirectoryMapper odm, Class<T> entityType) {

		Assert.notNull(ldapOperations, "LdapOperations must not be null!");
		Assert.notNull(odm, "ObjectDirectoryMapper must not be null!");
		Assert.notNull(entityType, "Entity type must not be null!");

		this.ldapOperations = ldapOperations;
		this.odm = odm;
		this.entityType = entityType;
	}

	// -------------------------------------------------------------------------
	// Methods from CrudRepository
	// -------------------------------------------------------------------------

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#save(java.lang.Object)
	 */
	@Override
	public <S extends T> S save(S entity) {

		Assert.notNull(entity, "Entity must not be null");

		Name declaredId = odm.getId(entity);

		if (isNew(entity, declaredId)) {
			ldapOperations.create(entity);
		} else {
			ldapOperations.update(entity);
		}

		return entity;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#saveAll(java.lang.Iterable)
	 */
	@Override
	public <S extends T> List<S> saveAll(Iterable<S> entities) {

		return StreamSupport.stream(entities.spliterator(), false) //
				.map(this::save) //
				.collect(Collectors.toList());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#findById(java.io.Serializable)
	 */
	@Override
	public Optional<T> findById(Name name) {

		Assert.notNull(name, "Id must not be null");

		try {
			return Optional.ofNullable(ldapOperations.findByDn(name, entityType));
		} catch (NameNotFoundException e) {
			return Optional.empty();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#existsById(java.io.Serializable)
	 */
	@Override
	public boolean existsById(Name name) {

		Assert.notNull(name, "Id must not be null");

		return findById(name).isPresent();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#findAll()
	 */
	@Override
	public List<T> findAll() {
		return ldapOperations.findAll(entityType);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#findAllById(java.lang.Iterable)
	 */
	@Override
	public List<T> findAllById(Iterable<Name> names) {

		return StreamSupport.stream(names.spliterator(), false) //
				.map(this::findById) //
				.flatMap(Optionals::toStream) //
				.collect(Collectors.toList());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#count()
	 */
	@Override
	public long count() {

		Filter filter = odm.filterFor(entityType, null);
		CountNameClassPairCallbackHandler callback = new CountNameClassPairCallbackHandler();
		LdapQuery query = query().attributes(OBJECTCLASS_ATTRIBUTE).filter(filter);
		ldapOperations.search(query, callback);

		return callback.getNoOfRows();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#deleteById(java.io.Serializable)
	 */
	@Override
	public void deleteById(Name name) {

		Assert.notNull(name, "Id must not be null");

		ldapOperations.unbind(name);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#delete(java.lang.Object)
	 */
	@Override
	public void delete(T entity) {

		Assert.notNull(entity, "Entity must not be null");

		ldapOperations.delete(entity);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#deleteAllById(java.lang.Iterable)
	 */
	@Override
	public void deleteAllById(Iterable<? extends Name> names) {

		Assert.notNull(names, "Names must not be null.");

		names.forEach(this::deleteById);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#deleteAll(java.lang.Iterable)
	 */
	@Override
	public void deleteAll(Iterable<? extends T> entities) {

		Assert.notNull(entities, "Entities must not be null.");

		entities.forEach(this::delete);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.CrudRepository#deleteAll()
	 */
	@Override
	public void deleteAll() {
		deleteAll(findAll());
	}

	// -------------------------------------------------------------------------
	// Methods from LdapRepository
	// ------------------------------------------------------------------------

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.ldap.repository.LdapRepository#findOne(org.springframework.ldap.query.LdapQuery)
	 */
	@Override
	public Optional<T> findOne(LdapQuery ldapQuery) {

		Assert.notNull(ldapQuery, "LdapQuery must not be null");

		try {
			return Optional.ofNullable(ldapOperations.findOne(ldapQuery, entityType));
		} catch (EmptyResultDataAccessException e) {
			return Optional.empty();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.ldap.repository.LdapRepository#findAll(org.springframework.ldap.query.LdapQuery)
	 */
	@Override
	public List<T> findAll(LdapQuery ldapQuery) {

		Assert.notNull(ldapQuery, "LdapQuery must not be null");
		return ldapOperations.find(ldapQuery, entityType);
	}


	private <S extends T> boolean isNew(S entity, @Nullable Name id) {

		if (entity instanceof Persistable) {
			Persistable<?> persistable = (Persistable<?>) entity;
			return persistable.isNew();
		} else {
			return id == null;
		}
	}


}
