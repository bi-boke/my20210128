package org.bok.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.GeneratedMessageV3;
import org.bok.common.utils.ForkController;
import org.bok.core.ChainBaseManager;
import org.bok.core.capsule.TransactionCapsule;
import org.bok.protos.Protocol.Transaction.Contract;
import org.bok.protos.Protocol.Transaction.Contract.ContractType;


public abstract class AbstractActuator implements Actuator {

  protected Any any;
  protected ChainBaseManager chainBaseManager;
  protected Contract contract;
  protected TransactionCapsule tx;
  protected ForkController forkController;

  public AbstractActuator(ContractType type, Class<? extends GeneratedMessageV3> clazz) {
    TransactionFactory.register(type, getClass(), clazz);
  }

  public Any getAny() {
    return any;
  }

  public AbstractActuator setAny(Any any) {
    this.any = any;
    return this;
  }

  public ChainBaseManager getChainBaseManager() {
    return chainBaseManager;
  }

  public AbstractActuator setChainBaseManager(ChainBaseManager chainBaseManager) {
    this.chainBaseManager = chainBaseManager;
    return this;
  }

  public Contract getContract() {
    return contract;
  }

  public AbstractActuator setContract(Contract contract) {
    this.contract = contract;
    this.any = contract.getParameter();
    return this;
  }

  public TransactionCapsule getTx() {
    return tx;
  }

  public AbstractActuator setTx(TransactionCapsule tx) {
    this.tx = tx;
    return this;
  }

  public ForkController getForkUtils() {
    return forkController;
  }

  public AbstractActuator setForkUtils(ForkController forkController) {
    this.forkController = forkController;
    return this;
  }
  
}
