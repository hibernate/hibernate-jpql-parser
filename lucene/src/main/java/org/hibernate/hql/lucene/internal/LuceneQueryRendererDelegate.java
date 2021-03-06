/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.hibernate.hql.lucene.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.hibernate.hql.ast.origin.hql.resolve.path.PropertyPath;
import org.hibernate.hql.ast.spi.EntityNamesResolver;
import org.hibernate.hql.ast.spi.SingleEntityQueryBuilder;
import org.hibernate.hql.ast.spi.SingleEntityQueryRendererDelegate;
import org.hibernate.hql.lucene.LuceneQueryParsingResult;
import org.hibernate.hql.lucene.internal.builder.LucenePropertyHelper;
import org.hibernate.search.engine.ProjectionConstants;
import org.hibernate.search.metadata.NumericFieldSettingsDescriptor.NumericEncodingType;

/**
 * Renderer delegate which creates Lucene queries targeting a single entity or a projection of the same.
 *
 * @author Gunnar Morling
 */
public class LuceneQueryRendererDelegate extends SingleEntityQueryRendererDelegate<Query, LuceneQueryParsingResult> {

	private final LucenePropertyHelper propertyHelper;

	private List<SortField> sortFields;

	public LuceneQueryRendererDelegate(EntityNamesResolver entityNames, SingleEntityQueryBuilder<Query> builder, Map<String, Object> namedParameters, LucenePropertyHelper propertyHelper) {
		super( propertyHelper, entityNames, builder, namedParameters );
		this.propertyHelper = propertyHelper;
	}

	@Override
	protected void addSortField(PropertyPath propertyPath, String collateName, boolean isAscending) {
		// collateName is ignored
		if ( sortFields == null ) {
			sortFields = new ArrayList<SortField>( 5 );
		}

		SortField.Type sortType = sortType( propertyPath );
		sortFields.add( new SortField( propertyPath.asStringPathWithoutAlias(), sortType, !isAscending ) );
	}

	private SortField.Type sortType(PropertyPath propertyPath) {
		NumericEncodingType numericEncodingType = propertyHelper.getNumericEncodingType( targetTypeName, propertyPath.getNodeNamesWithoutAlias() );
		// Determine sort type based on FieldBridgeType. SortField.BYTE and SortField.SHORT are not covered!
		if ( numericEncodingType != null ) {
			switch ( numericEncodingType ) {
				case INTEGER:
					return SortField.Type.INT;
				case LONG:
					return SortField.Type.LONG;
				case FLOAT:
					return SortField.Type.FLOAT;
				case DOUBLE:
					return SortField.Type.DOUBLE;
				default:
					return SortField.Type.STRING;
			}
		}
		return SortField.Type.STRING;
	}

	@Override
	public LuceneQueryParsingResult getResult() {
		Sort sort = null;
		if ( sortFields != null ) {
			sort = new Sort( sortFields.toArray( new SortField[sortFields.size()] ) );
		}
		return new LuceneQueryParsingResult( builder.build(), targetTypeName, targetType, projections, sort );
	}

	@Override
	public void setPropertyPath(PropertyPath propertyPath) {
		if ( status == Status.DEFINING_SELECT ) {
			if ( propertyPath.getNodes().size() == 1 && propertyPath.getNodes().get( 0 ).isAlias() ) {
				projections.add( ProjectionConstants.THIS );
			}
			else {
				List<String> names = resolveAlias( propertyPath );
				projections.add( join( names ) );
			}
		}
		else {
			this.propertyPath = propertyPath;
		}
	}

	private String join(List<String> names) {
		StringBuilder projection = new StringBuilder();
		for ( String name : names ) {
			projection.append( '.' );
			projection.append( name );
		}
		return projection.substring( 1 );
	}
}
