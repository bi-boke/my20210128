package org.bok.common.runtime.vm;

import static org.bok.common.utils.WalletUtil.generateContractAddress2;
import static org.bok.core.db.TransactionTrace.convertToUnAddress;

import java.util.Arrays;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.testng.Assert;
import org.bok.common.runtime.TVMTestResult;
import org.bok.common.runtime.TvmTestUtils;
import org.bok.common.utils.WalletUtil;
import org.bok.core.exception.ContractExeException;
import org.bok.core.exception.ContractValidateException;
import org.bok.core.exception.ReceiptCheckErrException;
import org.bok.core.exception.VMIllegalException;
import org.bok.core.utils.TransactionUtil;
import org.bok.core.vm.utils.MUtil;
import org.bok.protos.Protocol.Transaction;
import stest.bok.wallet.common.client.utils.AbiUtil;
import stest.bok.wallet.common.client.utils.DataWord;

@Slf4j
public class Create2Test extends VMTestBase {
  /*
  pragma solidity 0.5.0;
  contract Factory {
      event Deployed(address addr, uint256 salt);
      function deploy(bytes memory code, uint256 salt) public returns(address){
          address addr;
          assembly {
              addr := create2(0, add(code, 0x20), mload(code), salt)
              if iszero(extcodesize(addr)) {
                  revert(0, 0)
              }
          }
          emit Deployed(addr, salt);
          return addr;
      }
  }



  contract TestConstract {
      uint public i;
      constructor () public {
      }
      function plusOne() public returns(uint){
          i++;
      }
  }
   */

  /*
  contract:TestConstract
  deploy script:
  deploycontract TestConstract_0.5.0 [{"constant":false,"inputs":[],"name":"plusOne","outputs":
  [{"name":"","type":"uint256"}],"payable":false,"stateMutability":"nonpayable","type":"function"}
  ,{"constant":true,"inputs":[],"name":"i","outputs":[{"name":"","type":"uint256"}],"payable":false
  ,"stateMutability":"view","type":"function"},{"inputs":[],"payable":false,"stateMutability":
  "nonpayable","type":"constructor"}] 608060405234801561001057600080fd5b50d3801561001d57600080fd5b5
  0d2801561002a57600080fd5b5060d7806100396000396000f3fe608060405260043610602c5760003560e01c63ffffff
  ff16806368e5c066146031578063e5aa3d5814606d575b600080fd5b348015603c57600080fd5b50d3801560485760008
  0fd5b50d28015605457600080fd5b50605b6097565b60408051918252519081900360200190f35b348015607857600080
  fd5b50d38015608457600080fd5b50d28015609057600080fd5b50605b60a5565b600080546001019081905590565b600
  0548156fea165627a7a72305820c637cddbfa24b6530000f2e54d90e0f6c15907835674109287f64303446f9afb0029
  # # false 1000000000 100 10000000 0 0 #

  tirgger script:
  triggercontract Txxxxxxxxxxx plusOne() # false 1000000000 0 0 #
  triggercontract Txxxxxxxxxxx i() # false 1000000000 0 0 #


  contract:Factory
  deploy script:
  deploycontract Factory_0.5.0 [{"constant":false,"inputs":[{"name":"code","type":"bytes"},
  {"name":"salt","type":"uint256"}],"name":"deploy","outputs":[{"name":"","type":"address"}],
  "payable":false,"stateMutability":"nonpayable","type":"function"},{"anonymous":false,"inputs"
  :[{"indexed":false,"name":"addr","type":"address"},{"indexed":false,"name":"salt","type":
  "uint256"}],"name":"Deployed","type":"event"}] 608060405234801561001057600080fd5b50d3801561001d57
  600080fd5b50d2801561002a57600080fd5b506101c18061003a6000396000f3fe6080604052600436106100245760003
  560e01c63ffffffff1680639c4ae2d014610029575b600080fd5b34801561003557600080fd5b50d38015610042576000
  80fd5b50d2801561004f57600080fd5b506100f86004803603604081101561006657600080fd5b8101906020810181356
  4010000000081111561008157600080fd5b82018360208201111561009357600080fd5b80359060200191846001830284
  0111640100000000831117156100b557600080fd5b91908080601f0160208091040260200160405190810160405280939
  291908181526020018383808284376000920191909152509295505091359250610121915050565b6040805173ffffffff
  ffffffffffffffffffffffffffffffff9092168252519081900360200190f35b600080828451602086016000f59050803
  b151561013d57600080fd5b6040805173ffffffffffffffffffffffffffffffffffffffff831681526020810185905281
  517fb03c53b28e78a88e31607a27e1fa48234dce28d5d9d9ec7b295aeb02e674a1e1929181900390910190a1939250505
  056fea165627a7a7230582079653f6506bd7d3bdf4954ec98c452c5455d2b11444642db00b38fa422b25a650029 # #
  false 1000000000 100 10000000 0 0 #

  tirgger script:
  triggercontract Txxxxxxxxxxx deploy(bytes,uint256) bytes,uint256 false 1000000000 0 0 #



  */

  @Test
  public void testCreate2()
      throws ContractExeException, ReceiptCheckErrException,
      VMIllegalException, ContractValidateException {
    manager.getDynamicPropertiesStore().saveAllowTvmTransferTrc10(1);
    manager.getDynamicPropertiesStore().saveAllowTvmConstantinople(1);
    String contractName = "Factory_0";
    byte[] address = Hex.decode(OWNER_ADDRESS);
    String ABI = "[{\"constant\":false,\"inputs\":[{\"name\":\"code\",\"type\":\"bytes\"},"
        + "{\"name\":\"salt\",\"type\":\"uint256\"}],\"name\":\"deploy\",\"outputs\":[{\"name\""
        + ":\"\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"nonpayable\","
        + "\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"name\":"
        + "\"addr\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"salt\",\"type\":\"uint256\""
        + "}],\"name\":\"Deployed\",\"type\":\"event\"}]";

    String factoryCode = "608060405234801561001057600080fd5b50d3801561001d57600080fd5b50d2801561002"
        + "a57600080fd5b506101c18061003a6000396000f3fe6080604052600436106100245760003560e01c63fffff"
        + "fff1680639c4ae2d014610029575b600080fd5b34801561003557600080fd5b50d3801561004257600080fd5"
        + "b50d2801561004f57600080fd5b506100f86004803603604081101561006657600080fd5b810190602081018"
        + "13564010000000081111561008157600080fd5b82018360208201111561009357600080fd5b8035906020019"
        + "18460018302840111640100000000831117156100b557600080fd5b91908080601f016020809104026020016"
        + "0405190810160405280939291908181526020018383808284376000920191909152509295505091359250610"
        + "121915050565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200"
        + "190f35b600080828451602086016000f59050803b151561013d57600080fd5b6040805173fffffffffffffff"
        + "fffffffffffffffffffffffff831681526020810185905281517fb03c53b28e78a88e31607a27e1fa48234dc"
        + "e28d5d9d9ec7b295aeb02e674a1e1929181900390910190a1939250505056fea165627a7a7230582079653f6"
        + "506bd7d3bdf4954ec98c452c5455d2b11444642db00b38fa422b25a650029";

    String testCode = "608060405234801561001057600080fd5b50d3801561001d57600080fd5b50d2801561002a57"
        + "600080fd5b5060d7806100396000396000f3fe608060405260043610602c5760003560e01c63ffffffff1680"
        + "6368e5c066146031578063e5aa3d5814606d575b600080fd5b348015603c57600080fd5b50d3801560485760"
        + "0080fd5b50d28015605457600080fd5b50605b6097565b60408051918252519081900360200190f35b348015"
        + "607857600080fd5b50d38015608457600080fd5b50d28015609057600080fd5b50605b60a5565b6000805460"
        + "01019081905590565b6000548156fea165627a7a72305820c637cddbfa24b6530000f2e54d90e0f6c1590783"
        + "5674109287f64303446f9afb0029";
    long value = 0;
    long fee = 100000000;
    long consumeUserResourcePercent = 0;
    String methodSign = "deploy(bytes,uint256)";

    // deploy contract
    Transaction trx = TvmTestUtils.generateDeploySmartContractAndGetTransaction(
        contractName, address, ABI, factoryCode, value, fee, consumeUserResourcePercent,
        null);
    byte[] factoryAddress = WalletUtil.generateContractAddress(trx);
    runtime = TvmTestUtils.processTransactionAndReturnRuntime(trx, rootDeposit, null);
    Assert.assertNull(runtime.getRuntimeError());

    // Trigger contract method: deploy(bytes,uint)
    long salt = 100L;
    String hexInput = AbiUtil.parseMethod(methodSign, Arrays.asList(testCode, salt));
    TVMTestResult result = TvmTestUtils
        .triggerContractAndReturnTvmTestResult(Hex.decode(OWNER_ADDRESS),
            factoryAddress, Hex.decode(hexInput), 0, fee, manager, null);
    Assert.assertNull(result.getRuntime().getRuntimeError());

    byte[] returnValue = result.getRuntime().getResult().getHReturn();
    byte[] actualContract = convertToUnAddress(Arrays.copyOfRange(returnValue,
        12, 32));
    byte[] expectedContract =
        generateContractAddress2(address, new DataWord(salt).getData(), Hex.decode(testCode));
    // check deployed contract
    Assert.assertEquals(actualContract, expectedContract);

    // trigger deployed contract
    String methodToTrigger = "plusOne()";
    for (int i = 1; i < 3; i++) {
      hexInput = AbiUtil.parseMethod(methodToTrigger, Collections.emptyList());
      result = TvmTestUtils
          .triggerContractAndReturnTvmTestResult(Hex.decode(OWNER_ADDRESS),
              actualContract, Hex.decode(hexInput), 0, fee, manager, null);
      Assert.assertNull(result.getRuntime().getRuntimeError());
      Assert.assertEquals(result.getRuntime().getResult().getHReturn(), new DataWord(i).getData());
    }

    // deploy contract again
    result = TvmTestUtils
        .triggerContractAndReturnTvmTestResult(Hex.decode(OWNER_ADDRESS),
            factoryAddress, Hex.decode(hexInput), 0, fee, manager, null);
    Assert.assertNotNull(result.getRuntime().getRuntimeError());
    Assert.assertEquals(result.getRuntime().getRuntimeError(), "REVERT opcode executed");

  }

}
