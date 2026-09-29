package com.nantaaditya.sotres.entity;

import com.nantaaditya.sotres.helper.TsidHelper;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

public class TsidGenerator implements IdentifierGenerator {

  @Override
  public Object generate(SharedSessionContractImplementor session, Object object)
      throws HibernateException {
    return TsidHelper.generateStringId();
  }
}
