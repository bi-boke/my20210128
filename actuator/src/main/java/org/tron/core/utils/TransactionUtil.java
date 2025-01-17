/*
 * java-bok is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-bok is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.bok.core.utils;

import static org.bok.common.crypto.Hash.sha3omit12;

import com.google.common.base.CaseFormat;
import com.google.common.primitives.Longs;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.bok.api.GrpcAPI.Return;
import org.bok.api.GrpcAPI.Return.response_code;
import org.bok.api.GrpcAPI.TransactionExtention;
import org.bok.api.GrpcAPI.TransactionSignWeight;
import org.bok.api.GrpcAPI.TransactionSignWeight.Result;
import org.bok.common.parameter.CommonParameter;
import org.bok.common.utils.Sha256Hash;
import org.bok.core.ChainBaseManager;
import org.bok.core.capsule.AccountCapsule;
import org.bok.core.capsule.TransactionCapsule;
import org.bok.core.exception.PermissionException;
import org.bok.core.exception.SignatureFormatException;
import org.bok.protos.Protocol.Permission;
import org.bok.protos.Protocol.Permission.PermissionType;
import org.bok.protos.Protocol.Transaction;
import org.bok.protos.Protocol.Transaction.Contract;
import org.bok.protos.Protocol.Transaction.Result.contractResult;
import org.bok.protos.Protocol.TransactionSign;
import org.bok.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.bok.protos.contract.SmartContractOuterClass.TriggerSmartContract;

@Slf4j(topic = "capsule")
@Component
public class TransactionUtil {

  private static final int maxAccountNameLen = 200;
  private static final int maxAccountIdLen = 32;
  private static final int minAccountIdLen = 8;
  private static final int maxAssetNameLen = 32;
  private static final int maxTokenAbbrNameLen = 5;

  @Autowired
  private ChainBaseManager chainBaseManager;

  public static boolean validAccountName(byte[] accountName) {
    if (ArrayUtils.isEmpty(accountName)) {
      return true;   //account name can be empty
    }

    return accountName.length <= maxAccountNameLen;
  }

  public static boolean validAccountId(byte[] accountId) {
    return validReadableBytes(accountId, maxAccountIdLen) && accountId.length >= minAccountIdLen;
  }

  public static boolean validAssetName(byte[] assetName) {
    return validReadableBytes(assetName, maxAssetNameLen);
  }

  public static boolean validTokenAbbrName(byte[] abbrName) {
    return validReadableBytes(abbrName, maxTokenAbbrNameLen);
  }

  private static boolean validReadableBytes(byte[] bytes, int maxLength) {
    if (ArrayUtils.isEmpty(bytes) || bytes.length > maxLength) {
      return false;
    }
    // b must be readable
    for (byte b : bytes) {
      if (b < 0x21) {
        return false; // 0x21 = '!'
      }
      if (b > 0x7E) {
        return false; // 0x7E = '~'
      }
    }
    return true;
  }

  public static boolean validAssetDescription(byte[] description) {
    if (ArrayUtils.isEmpty(description)) {
      return true;   //description can empty
    }

    return description.length <= 200;
  }

  public static boolean validUrl(byte[] url) {
    if (ArrayUtils.isEmpty(url)) {
      return false;
    }
    return url.length <= 256;
  }

  public static boolean isNumber(byte[] id) {
    if (ArrayUtils.isEmpty(id)) {
      return false;
    }
    for (byte b : id) {
      if (b < '0' || b > '9') {
        return false;
      }
    }

    return !(id.length > 1 && id[0] == '0');
  }

  public static Sha256Hash getTransactionId(Transaction transaction) {
    return Sha256Hash.of(CommonParameter.getInstance().isECKeyCryptoEngine(),
        transaction.getRawData().toByteArray());
  }


  public static contractResult getContractRet(Transaction transaction) {
    if (transaction.getRetCount() <= 0) {
      return null;
    }
    return transaction.getRet(0).getContractRet();
  }

  public static long getCallTokenValue(Transaction.Contract contract) {
    try {
      Any contractParameter = contract.getParameter();
      switch (contract.getType()) {
        case TriggerSmartContract:
          return contractParameter.unpack(TriggerSmartContract.class).getCallTokenValue();

        case CreateSmartContract:
          return contractParameter.unpack(CreateSmartContract.class).getCallTokenValue();
        default:
          return 0L;
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage());
      return 0L;
    }
  }

  public static byte[] generateContractAddress(byte[] ownerAddress, byte[] txRawDataHash) {

    byte[] combined = new byte[txRawDataHash.length + ownerAddress.length];
    System.arraycopy(txRawDataHash, 0, combined, 0, txRawDataHash.length);
    System.arraycopy(ownerAddress, 0, combined, txRawDataHash.length, ownerAddress.length);

    return sha3omit12(combined);

  }

  // for `CREATE`
  public static byte[] generateContractAddress(byte[] transactionRootId, long nonce) {
    byte[] nonceBytes = Longs.toByteArray(nonce);
    byte[] combined = new byte[transactionRootId.length + nonceBytes.length];
    System.arraycopy(transactionRootId, 0, combined, 0, transactionRootId.length);
    System.arraycopy(nonceBytes, 0, combined, transactionRootId.length, nonceBytes.length);

    return sha3omit12(combined);
  }

  public static boolean checkPermissionOperations(Permission permission, Contract contract)
      throws PermissionException {
    ByteString operations = permission.getOperations();
    if (operations.size() != 32) {
      throw new PermissionException("operations size must be 32");
    }
    int contractType = contract.getTypeValue();
    boolean b = (operations.byteAt(contractType / 8) & (1 << (contractType % 8))) != 0;
    return b;
  }

  public static String makeUpperCamelMethod(String originName) {
    return "get" + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, originName)
        .replace("_", "");
  }

  public static TransactionCapsule getTransactionSign(TransactionSign transactionSign) {
    byte[] privateKey = transactionSign.getPrivateKey().toByteArray();
    TransactionCapsule trx = new TransactionCapsule(transactionSign.getTransaction());
    trx.sign(privateKey);
    return trx;
  }

  public TransactionCapsule addSign(TransactionSign transactionSign)
      throws PermissionException, SignatureException, SignatureFormatException {
    byte[] privateKey = transactionSign.getPrivateKey().toByteArray();
    TransactionCapsule trx = new TransactionCapsule(transactionSign.getTransaction());
    trx.addSign(privateKey, chainBaseManager.getAccountStore());
    return trx;
  }

  public TransactionSignWeight getTransactionSignWeight(Transaction trx) {
    TransactionSignWeight.Builder tswBuilder = TransactionSignWeight.newBuilder();
    TransactionExtention.Builder trxExBuilder = TransactionExtention.newBuilder();
    trxExBuilder.setTransaction(trx);
    trxExBuilder.setTxid(ByteString.copyFrom(Sha256Hash.hash(CommonParameter
        .getInstance().isECKeyCryptoEngine(), trx.getRawData().toByteArray())));
    Return.Builder retBuilder = Return.newBuilder();
    retBuilder.setResult(true).setCode(response_code.SUCCESS);
    trxExBuilder.setResult(retBuilder);
    tswBuilder.setTransaction(trxExBuilder);
    Result.Builder resultBuilder = Result.newBuilder();
    try {
      Contract contract = trx.getRawData().getContract(0);
      byte[] owner = TransactionCapsule.getOwner(contract);
      AccountCapsule account = chainBaseManager.getAccountStore().get(owner);
      if (account == null) {
        throw new PermissionException("Account does not exist!");
      }
      int permissionId = contract.getPermissionId();
      Permission permission = account.getPermissionById(permissionId);
      if (permission == null) {
        throw new PermissionException("Permission for this, does not exist!");
      }
      if (permissionId != 0) {
        if (permission.getType() != PermissionType.Active) {
          throw new PermissionException("Permission type is wrong!");
        }
        //check operations
        if (!checkPermissionOperations(permission, contract)) {
          throw new PermissionException("Permission denied!");
        }
      }
      tswBuilder.setPermission(permission);
      if (trx.getSignatureCount() > 0) {
        List<ByteString> approveList = new ArrayList<ByteString>();
        long currentWeight = TransactionCapsule.checkWeight(permission, trx.getSignatureList(),
            Sha256Hash.hash(CommonParameter.getInstance()
                .isECKeyCryptoEngine(), trx.getRawData().toByteArray()), approveList);
        tswBuilder.addAllApprovedList(approveList);
        tswBuilder.setCurrentWeight(currentWeight);
      }
      if (tswBuilder.getCurrentWeight() >= permission.getThreshold()) {
        resultBuilder.setCode(Result.response_code.ENOUGH_PERMISSION);
      } else {
        resultBuilder.setCode(Result.response_code.NOT_ENOUGH_PERMISSION);
      }
    } catch (SignatureFormatException signEx) {
      resultBuilder.setCode(Result.response_code.SIGNATURE_FORMAT_ERROR);
      resultBuilder.setMessage(signEx.getMessage());
    } catch (SignatureException signEx) {
      resultBuilder.setCode(Result.response_code.COMPUTE_ADDRESS_ERROR);
      resultBuilder.setMessage(signEx.getMessage());
    } catch (PermissionException permEx) {
      resultBuilder.setCode(Result.response_code.PERMISSION_ERROR);
      resultBuilder.setMessage(permEx.getMessage());
    } catch (Exception ex) {
      resultBuilder.setCode(Result.response_code.OTHER_ERROR);
      resultBuilder.setMessage(ex.getClass() + " : " + ex.getMessage());
    }
    tswBuilder.setResult(resultBuilder);
    return tswBuilder.build();
  }

}
