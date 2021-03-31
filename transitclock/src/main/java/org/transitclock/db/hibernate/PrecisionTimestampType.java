/* 
 * This file is part of Transitime.org
 * 
 * Transitime.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL) as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * Transitime.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Transitime.org .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.transitclock.db.hibernate;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.TimestampType;
import org.hibernate.usertype.UserType;

/**
 * The purpose of this class was to have a custom hibernate type for a 
 * Timestamp column so that could write fractional seconds. But it turns
 * out that the problems having to do with fractional seconds were based
 * only on the version of the Connector/J JDBC driver that was being used.
 * As long as using at least MySQL 5.6.4 and Connector/J 5.1.23 then
 * fractional seconds already work. The only reason for using this class
 * is if want to have Hibernate log fractional seconds when logging
 * the SQL parameters.
 * 
 * To enable use
 * @Type (type="org.transitclock.db.hibernate.PrecisionTimestampType")
 * instead of @Temporal(TemporalType.TIMESTAMP) when declaring a Java
 * object that is to be persisted.
 * 
 * @author SkiBu Smith
 *
 */
public class PrecisionTimestampType implements UserType {

	/********************** Member Functions **************************/
	@Override
	public int[] sqlTypes() {
        return new int[] {
        		TimestampType.INSTANCE.sqlType(),
        };
    }
    
	/* (non-Javadoc)
	 * @see org.hibernate.usertype.UserType#assemble(java.io.Serializable, java.lang.Object)
	 */
	@Override
	public Object assemble(Serializable cached, Object owner)
			throws HibernateException {
		return cached;
	}

	/* (non-Javadoc)
	 * @see org.hibernate.usertype.UserType#deepCopy(java.lang.Object)
	 */
	@Override
	public Object deepCopy(Object value) throws HibernateException {
		return new Timestamp(((Timestamp) value).getTime());
	}

	/* (non-Javadoc)
	 * @see org.hibernate.usertype.UserType#disassemble(java.lang.Object)
	 */
	@Override
	public Serializable disassemble(Object value) throws HibernateException {
		return (Serializable) value;
	}

	/* (non-Javadoc)
	 * @see org.hibernate.usertype.UserType#equals(java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean equals(Object x, Object y) throws HibernateException {
		return x == y;
	}

	/* (non-Javadoc)
	 * @see org.hibernate.usertype.UserType#hashCode(java.lang.Object)
	 */
	@Override
	public int hashCode(Object x) throws HibernateException {
		return x.hashCode();
	}

	/* (non-Javadoc)
	 * @see org.hibernate.usertype.UserType#isMutable()
	 */
	@Override
	public boolean isMutable() {
		return false;
	}
	


	/* (non-Javadoc)
	 * @see org.hibernate.usertype.UserType#nullSafeGet(java.sql.ResultSet, java.lang.String[], org.hibernate.engine.spi.SessionImplementor, java.lang.Object)
	 */
	@Override
	public Object nullSafeGet(ResultSet rs, String[] names,
			SharedSessionContractImplementor session, Object owner)
			throws HibernateException, SQLException {
		assert names.length == 1;
		
		Timestamp timestamp;
		if (rs != null && !rs.wasNull()) {
			timestamp = rs.getTimestamp(names[0]);	
		} else {
			timestamp = new Timestamp(0);
		}
		
		return timestamp;
	}

	/* (non-Javadoc)
	 * @see org.hibernate.usertype.UserType#nullSafeSet(java.sql.PreparedStatement, java.lang.Object, int, org.hibernate.engine.spi.SessionImplementor)
	 */
	@Override
	public void nullSafeSet(PreparedStatement st, Object value, int index,
			SharedSessionContractImplementor session) throws HibernateException, SQLException {
		if ( value != null ) {
//			System.out.println("value.class=" + value.getClass().getName());
//			System.out.println("value=" + value);
			if (!(value instanceof Date)) 
				throw new HibernateException("Writing element " + value.getClass().getName() +
						" but was expecting a java.util.Date");
			Date d = (Date) value;
			Timestamp ts = new Timestamp(d.getTime());
//			System.out.println("d=" + Time.dateStrMsec(d) + " =" + d.getTime() + " msec");
//			System.out.println("ts=" + ts);
			// Tried using the following line for dealing with prepared statement
			// but then could not log the parameter value when logging the sql.
			// Therefore need to use TimestampType.INSTANCE.set(). 
			//st.setTimestamp(index, new Timestamp(d.getTime())); 
			TimestampType.INSTANCE.set(st, ts, index, session);
        } else {
        	TimestampType.INSTANCE.set(st, null, index, session);
        }
	}

	/* (non-Javadoc)
	 * @see org.hibernate.usertype.UserType#replace(java.lang.Object, java.lang.Object, java.lang.Object)
	 */
	@Override
	public Object replace(Object original, Object target, Object owner)
			throws HibernateException {
		return original;
	}

	/* (non-Javadoc)
	 * @see org.hibernate.usertype.UserType#returnedClass()
	 */
	@Override
	public Class<?> returnedClass() {
		return Timestamp.class;
	}

	

}
