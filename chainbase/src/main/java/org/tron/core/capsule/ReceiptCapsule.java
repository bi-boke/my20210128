package org.bok.core.capsule;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.bok.common.parameter.CommonParameter;
import org.bok.common.utils.Commons;
import org.bok.common.utils.ForkController;
import org.bok.common.utils.Sha256Hash;
import org.bok.common.utils.StringUtil;
import org.bok.core.Constant;
import org.bok.core.config.Parameter.ForkBlockVersionEnum;
import org.bok.core.db.EnergyProcessor;
import org.bok.core.exception.BalanceInsufficientException;
import org.bok.core.store.AccountStore;
import org.bok.core.store.DynamicPropertiesStore;
import org.bok.protos.Protocol.ResourceReceipt;
import org.bok.protos.Protocol.Transaction.Result.contractResult;

public class ReceiptCapsule {

  private ResourceReceipt receipt;
  @Getter
  @Setter
  private long multiSignFee;

  private Sha256Hash receiptAddress;

  public ReceiptCapsule(ResourceReceipt data, Sha256Hash receiptAddress) {
    this.receipt = data;
    this.receiptAddress = receiptAddress;
  }

  public ReceiptCapsule(Sha256Hash receiptAddress) {
    this.receipt = ResourceReceipt.newBuilder().build();
    this.receiptAddress = receiptAddress;
  }

  public static ResourceReceipt copyReceipt(ReceiptCapsule origin) {
    return origin.getReceipt().toBuilder().build();
  }

  public static boolean checkForEnergyLimit(DynamicPropertiesStore ds) {
    long blockNum = ds.getLatestBlockHeaderNumber();
    return blockNum >= CommonParameter.getInstance()
        .getBlockNumForEneryLimit();
  }

  public ResourceReceipt getReceipt() {
    return this.receipt;
  }

  public void setReceipt(ResourceReceipt receipt) {
    this.receipt = receipt;
  }

  public Sha256Hash getReceiptAddress() {
    return this.receiptAddress;
  }

  public void addNetFee(long netFee) {
    this.receipt = this.receipt.toBuilder().setNetFee(getNetFee() + netFee).build();
  }

  public long getEnergyUsage() {
    return this.receipt.getEnergyUsage();
  }

  public void setEnergyUsage(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setEnergyUsage(energyUsage).build();
  }

  public long getEnergyFee() {
    return this.receipt.getEnergyFee();
  }

  public void setEnergyFee(long energyFee) {
    this.receipt = this.receipt.toBuilder().setEnergyFee(energyFee).build();
  }

  public long getOriginEnergyUsage() {
    return this.receipt.getOriginEnergyUsage();
  }

  public void setOriginEnergyUsage(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setOriginEnergyUsage(energyUsage).build();
  }

  public long getEnergyUsageTotal() {
    return this.receipt.getEnergyUsageTotal();
  }

  public void setEnergyUsageTotal(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setEnergyUsageTotal(energyUsage).build();
  }

  public long getNetUsage() {
    return this.receipt.getNetUsage();
  }

  public void setNetUsage(long netUsage) {
    this.receipt = this.receipt.toBuilder().setNetUsage(netUsage).build();
  }

  public long getNetFee() {
    return this.receipt.getNetFee();
  }

  public void setNetFee(long netFee) {
    this.receipt = this.receipt.toBuilder().setNetFee(netFee).build();
  }

  /**
   * payEnergyBill pay receipt energy bill by energy processor.
   */
  public void payEnergyBill(DynamicPropertiesStore dynamicPropertiesStore,
      AccountStore accountStore, ForkController forkController, AccountCapsule origin, AccountCapsule caller,
      long percent, long originEnergyLimit, EnergyProcessor energyProcessor, long now)
      throws BalanceInsufficientException {
    if (receipt.getEnergyUsageTotal() <= 0) {
      return;
    }

    if (Objects.isNull(origin) && dynamicPropertiesStore.getAllowTvmConstantinople() == 1) {
      payEnergyBill(dynamicPropertiesStore, accountStore, forkController, caller,
          receipt.getEnergyUsageTotal(), energyProcessor, now);
      return;
    }

    if (caller.getAddress().equals(origin.getAddress())) {
      payEnergyBill(dynamicPropertiesStore, accountStore, forkController, caller,
          receipt.getEnergyUsageTotal(), energyProcessor, now);
    } else {
      long originUsage = Math.multiplyExact(receipt.getEnergyUsageTotal(), percent) / 100;
      originUsage = getOriginUsage(dynamicPropertiesStore, origin, originEnergyLimit,
          energyProcessor,
          originUsage);

      long callerUsage = receipt.getEnergyUsageTotal() - originUsage;
      energyProcessor.useEnergy(origin, originUsage, now);
      this.setOriginEnergyUsage(originUsage);
      payEnergyBill(dynamicPropertiesStore, accountStore, forkController,
          caller, callerUsage, energyProcessor, now);
    }
  }

  private long getOriginUsage(DynamicPropertiesStore dynamicPropertiesStore, AccountCapsule origin,
      long originEnergyLimit,
      EnergyProcessor energyProcessor, long originUsage) {

    if (checkForEnergyLimit(dynamicPropertiesStore)) {
      return Math.min(originUsage,
          Math.min(energyProcessor.getAccountLeftEnergyFromFreeze(origin), originEnergyLimit));
    }
    return Math.min(originUsage, energyProcessor.getAccountLeftEnergyFromFreeze(origin));
  }

  private void payEnergyBill(
      DynamicPropertiesStore dynamicPropertiesStore, AccountStore accountStore, ForkController forkController,
      AccountCapsule account,
      long usage,
      EnergyProcessor energyProcessor,
      long now) throws BalanceInsufficientException {
    long accountEnergyLeft = energyProcessor.getAccountLeftEnergyFromFreeze(account);
    if (accountEnergyLeft >= usage) {
      energyProcessor.useEnergy(account, usage, now);
      this.setEnergyUsage(usage);
    } else {
      energyProcessor.useEnergy(account, accountEnergyLeft, now);

      if (forkController.pass(ForkBlockVersionEnum.VERSION_3_6_5) &&
          dynamicPropertiesStore.getAllowAdaptiveEnergy() == 1) {
        long blockEnergyUsage =
            dynamicPropertiesStore.getBlockEnergyUsage() + (usage - accountEnergyLeft);
        dynamicPropertiesStore.saveBlockEnergyUsage(blockEnergyUsage);
      }

      long sunPerEnergy = Constant.SUN_PER_ENERGY;
      long dynamicEnergyFee = dynamicPropertiesStore.getEnergyFee();
      if (dynamicEnergyFee > 0) {
        sunPerEnergy = dynamicEnergyFee;
      }
      long energyFee =
          (usage - accountEnergyLeft) * sunPerEnergy;
      this.setEnergyUsage(accountEnergyLeft);
      this.setEnergyFee(energyFee);
      long balance = account.getBalance();
      if (balance < energyFee) {
        throw new BalanceInsufficientException(
            StringUtil.createReadableString(account.createDbKey()) + " insufficient balance");
      }
      account.setBalance(balance - energyFee);

      //send to blackHole
      Commons.adjustBalance(accountStore, accountStore.getBlackhole().getAddress().toByteArray(),
          energyFee);
    }

    accountStore.put(account.getAddress().toByteArray(), account);
  }

  public contractResult getResult() {
    return this.receipt.getResult();
  }

  public void setResult(contractResult success) {
    this.receipt = receipt.toBuilder().setResult(success).build();
  }
}
