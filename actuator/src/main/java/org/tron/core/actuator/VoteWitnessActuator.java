package org.bok.core.actuator;

import static org.bok.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.bok.core.actuator.ActuatorConstant.NOT_EXIST_STR;
import static org.bok.core.actuator.ActuatorConstant.WITNESS_EXCEPTION_STR;
import static org.bok.core.config.Parameter.ChainConstant.MAX_VOTE_NUMBER;
import static org.bok.core.config.Parameter.ChainConstant.UN_PRECISION;

import com.google.common.math.LongMath;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Iterator;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.bok.common.utils.ByteArray;
import org.bok.common.utils.DecodeUtil;
import org.bok.common.utils.StringUtil;
import org.bok.core.capsule.AccountCapsule;
import org.bok.core.capsule.TransactionResultCapsule;
import org.bok.core.capsule.VotesCapsule;
import org.bok.core.db.DelegationService;
import org.bok.core.exception.ContractExeException;
import org.bok.core.exception.ContractValidateException;
import org.bok.core.store.AccountStore;
import org.bok.core.store.VotesStore;
import org.bok.core.store.WitnessStore;
import org.bok.protos.Protocol.Transaction.Contract.ContractType;
import org.bok.protos.Protocol.Transaction.Result.code;
import org.bok.protos.contract.WitnessContract.VoteWitnessContract;
import org.bok.protos.contract.WitnessContract.VoteWitnessContract.Vote;

@Slf4j(topic = "actuator")
public class VoteWitnessActuator extends AbstractActuator {


  public VoteWitnessActuator() {
    super(ContractType.VoteWitnessContract, VoteWitnessContract.class);
  }

  @Override
  public boolean execute(Object object) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) object;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    try {
      VoteWitnessContract voteContract = any.unpack(VoteWitnessContract.class);
      countVoteAccount(voteContract);
      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.any == null) {
      throw new ContractValidateException(ActuatorConstant.CONTRACT_NOT_EXIST);
    }
    if (chainBaseManager == null) {
      throw new ContractValidateException(ActuatorConstant.STORE_NOT_EXIST);
    }
    AccountStore accountStore = chainBaseManager.getAccountStore();
    WitnessStore witnessStore = chainBaseManager.getWitnessStore();
    if (!this.any.is(VoteWitnessContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [VoteWitnessContract], real type[" + any
              .getClass() + "]");
    }
    final VoteWitnessContract contract;
    try {
      contract = this.any.unpack(VoteWitnessContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    if (!DecodeUtil.addressValid(contract.getOwnerAddress().toByteArray())) {
      throw new ContractValidateException("Invalid address");
    }
    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    if (contract.getVotesCount() == 0) {
      throw new ContractValidateException(
          "VoteNumber must more than 0");
    }
    int maxVoteNumber = MAX_VOTE_NUMBER;
    if (contract.getVotesCount() > maxVoteNumber) {
      throw new ContractValidateException(
          "VoteNumber more than maxVoteNumber " + maxVoteNumber);
    }
    try {
      Iterator<Vote> iterator = contract.getVotesList().iterator();
      Long sum = 0L;
      while (iterator.hasNext()) {
        Vote vote = iterator.next();
        byte[] witnessCandidate = vote.getVoteAddress().toByteArray();
        if (!DecodeUtil.addressValid(witnessCandidate)) {
          throw new ContractValidateException("Invalid vote address!");
        }
        long voteCount = vote.getVoteCount();
        if (voteCount <= 0) {
          throw new ContractValidateException("vote count must be greater than 0");
        }
        String readableWitnessAddress = StringUtil.createReadableString(vote.getVoteAddress());
        if (!accountStore.has(witnessCandidate)) {
          throw new ContractValidateException(
              ACCOUNT_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
        }
        if (!witnessStore.has(witnessCandidate)) {
          throw new ContractValidateException(
              WITNESS_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
        }
        sum = LongMath.checkedAdd(sum, vote.getVoteCount());
      }

      AccountCapsule accountCapsule = accountStore.get(ownerAddress);
      if (accountCapsule == null) {
        throw new ContractValidateException(
            ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      }

      long unPower = accountCapsule.getUnPower();

      sum = LongMath.checkedMultiply(sum, UN_PRECISION); //trx -> drop. The vote count is based on UN
      if (sum > unPower) {
        throw new ContractValidateException(
            "The total number of votes[" + sum + "] is greater than the unPower[" + unPower
                + "]");
      }
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    return true;
  }

  private void countVoteAccount(VoteWitnessContract voteContract) {
    AccountStore accountStore = chainBaseManager.getAccountStore();
    VotesStore votesStore = chainBaseManager.getVotesStore();
    DelegationService delegationService = chainBaseManager.getDelegationService();
    byte[] ownerAddress = voteContract.getOwnerAddress().toByteArray();

    VotesCapsule votesCapsule;

    //
    delegationService.withdrawReward(ownerAddress);

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);

    if (!votesStore.has(ownerAddress)) {
      votesCapsule = new VotesCapsule(voteContract.getOwnerAddress(),
          accountCapsule.getVotesList());
    } else {
      votesCapsule = votesStore.get(ownerAddress);
    }

    accountCapsule.clearVotes();
    votesCapsule.clearNewVotes();

    voteContract.getVotesList().forEach(vote -> {
      logger.debug("countVoteAccount, address[{}]",
          ByteArray.toHexString(vote.getVoteAddress().toByteArray()));

      votesCapsule.addNewVotes(vote.getVoteAddress(), vote.getVoteCount());
      accountCapsule.addVotes(vote.getVoteAddress(), vote.getVoteCount());
    });

    accountStore.put(accountCapsule.createDbKey(), accountCapsule);
    votesStore.put(ownerAddress, votesCapsule);
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(VoteWitnessContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
