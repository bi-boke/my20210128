package org.bok.core.services.interfaceOnSolidity;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.bok.api.DatabaseGrpc.DatabaseImplBase;
import org.bok.api.GrpcAPI;
import org.bok.api.GrpcAPI.AddressPrKeyPairMessage;
import org.bok.api.GrpcAPI.AssetIssueList;
import org.bok.api.GrpcAPI.BlockExtention;
import org.bok.api.GrpcAPI.BlockReference;
import org.bok.api.GrpcAPI.BytesMessage;
import org.bok.api.GrpcAPI.DelegatedResourceList;
import org.bok.api.GrpcAPI.DelegatedResourceMessage;
import org.bok.api.GrpcAPI.EmptyMessage;
import org.bok.api.GrpcAPI.ExchangeList;
import org.bok.api.GrpcAPI.NoteParameters;
import org.bok.api.GrpcAPI.NumberMessage;
import org.bok.api.GrpcAPI.PaginatedMessage;
import org.bok.api.GrpcAPI.Return;
import org.bok.api.GrpcAPI.Return.response_code;
import org.bok.api.GrpcAPI.SpendResult;
import org.bok.api.GrpcAPI.TransactionExtention;
import org.bok.api.GrpcAPI.TransactionInfoList;
import org.bok.api.GrpcAPI.WitnessList;
import org.bok.api.WalletSolidityGrpc.WalletSolidityImplBase;
import org.bok.common.application.Service;
import org.bok.common.crypto.SignInterface;
import org.bok.common.crypto.SignUtils;
import org.bok.common.parameter.CommonParameter;
import org.bok.common.utils.Sha256Hash;
import org.bok.common.utils.StringUtil;
import org.bok.common.utils.Utils;
import org.bok.core.capsule.BlockCapsule;
import org.bok.core.config.args.Args;
import org.bok.core.services.RpcApiService;
import org.bok.core.services.ratelimiter.RateLimiterInterceptor;
import org.bok.protos.Protocol.Account;
import org.bok.protos.Protocol.Block;
import org.bok.protos.Protocol.DynamicProperties;
import org.bok.protos.Protocol.Exchange;
import org.bok.protos.Protocol.Transaction;
import org.bok.protos.Protocol.TransactionInfo;
import org.bok.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.bok.protos.contract.ShieldContract.IncrementalMerkleVoucherInfo;
import org.bok.protos.contract.ShieldContract.OutputPointInfo;
import org.bok.protos.contract.SmartContractOuterClass.TriggerSmartContract;

@Slf4j(topic = "API")
public class RpcApiServiceOnSolidity implements Service {

  private int port = Args.getInstance().getRpcOnSolidityPort();
  private Server apiServer;

  @Autowired
  private WalletOnSolidity walletOnSolidity;

  @Autowired
  private RpcApiService rpcApiService;

  @Autowired
  private RateLimiterInterceptor rateLimiterInterceptor;

  @Override
  public void init() {
  }

  @Override
  public void init(CommonParameter args) {
  }

  @Override
  public void start() {
    try {
      NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(port)
          .addService(new DatabaseApi());

      CommonParameter parameter = Args.getInstance();

      if (parameter.getRpcThreadNum() > 0) {
        serverBuilder = serverBuilder
            .executor(Executors.newFixedThreadPool(parameter.getRpcThreadNum()));
      }

      serverBuilder = serverBuilder.addService(new WalletSolidityApi());

      // Set configs from config.conf or default value
      serverBuilder.maxConcurrentCallsPerConnection(parameter.getMaxConcurrentCallsPerConnection())
          .flowControlWindow(parameter.getFlowControlWindow())
          .maxConnectionIdle(parameter.getMaxConnectionIdleInMillis(), TimeUnit.MILLISECONDS)
          .maxConnectionAge(parameter.getMaxConnectionAgeInMillis(), TimeUnit.MILLISECONDS)
          .maxMessageSize(parameter.getMaxMessageSize())
          .maxHeaderListSize(parameter.getMaxHeaderListSize());

      // add a ratelimiter interceptor
      serverBuilder.intercept(rateLimiterInterceptor);

      apiServer = serverBuilder.build();
      rateLimiterInterceptor.init(apiServer);

      apiServer.start();

    } catch (IOException e) {
      logger.debug(e.getMessage(), e);
    }

    logger.info("RpcApiServiceOnSolidity started, listening on " + port);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.err.println("*** shutting down gRPC server on solidity since JVM is shutting down");
      //server.this.stop();
      System.err.println("*** server on solidity shut down");
    }));
  }

  private TransactionExtention transaction2Extention(Transaction transaction) {
    if (transaction == null) {
      return null;
    }
    TransactionExtention.Builder trxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    trxExtBuilder.setTransaction(transaction);
    trxExtBuilder.setTxid(Sha256Hash.of(CommonParameter.getInstance().isECKeyCryptoEngine(),
        transaction.getRawData().toByteArray()).getByteString());
    retBuilder.setResult(true).setCode(response_code.SUCCESS);
    trxExtBuilder.setResult(retBuilder);
    return trxExtBuilder.build();
  }

  private BlockExtention block2Extention(Block block) {
    if (block == null) {
      return null;
    }
    BlockExtention.Builder builder = BlockExtention.newBuilder();
    BlockCapsule blockCapsule = new BlockCapsule(block);
    builder.setBlockHeader(block.getBlockHeader());
    builder.setBlockid(ByteString.copyFrom(blockCapsule.getBlockId().getBytes()));
    for (int i = 0; i < block.getTransactionsCount(); i++) {
      Transaction transaction = block.getTransactions(i);
      builder.addTransactions(transaction2Extention(transaction));
    }
    return builder.build();
  }

  @Override
  public void stop() {
    if (apiServer != null) {
      apiServer.shutdown();
    }
  }

  /**
   * DatabaseApi.
   */
  private class DatabaseApi extends DatabaseImplBase {

    @Override
    public void getBlockReference(EmptyMessage request,
        StreamObserver<BlockReference> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getDatabaseApi().getBlockReference(request, responseObserver));
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      walletOnSolidity
          .futureGet(() -> rpcApiService.getDatabaseApi().getNowBlock(request, responseObserver));
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      walletOnSolidity
          .futureGet(() -> rpcApiService.getDatabaseApi().getBlockByNum(request, responseObserver));
    }

    @Override
    public void getDynamicProperties(EmptyMessage request,
        StreamObserver<DynamicProperties> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getDatabaseApi().getDynamicProperties(request, responseObserver));
    }
  }

  /**
   * WalletSolidityApi.
   */
  private class WalletSolidityApi extends WalletSolidityImplBase {

    @Override
    public void getAccount(Account request, StreamObserver<Account> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getAccount(request, responseObserver));
    }

    @Override
    public void getAccountById(Account request, StreamObserver<Account> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getAccountById(request, responseObserver));
    }

    @Override
    public void listWitnesses(EmptyMessage request, StreamObserver<WitnessList> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().listWitnesses(request, responseObserver));
    }

    @Override
    public void getAssetIssueById(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getAssetIssueById(request, responseObserver));
    }

    @Override
    public void getAssetIssueByName(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .getAssetIssueByName(request, responseObserver));
    }

    @Override
    public void getAssetIssueList(EmptyMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getAssetIssueList(request, responseObserver));
    }

    @Override
    public void getAssetIssueListByName(BytesMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .getAssetIssueListByName(request, responseObserver));
    }

    @Override
    public void getPaginatedAssetIssueList(PaginatedMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .getPaginatedAssetIssueList(request, responseObserver));
    }

    @Override
    public void getExchangeById(BytesMessage request, StreamObserver<Exchange> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getExchangeById(request, responseObserver));
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getNowBlock(request, responseObserver));
    }

    @Override
    public void getNowBlock2(EmptyMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getNowBlock2(request, responseObserver));

    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getBlockByNum(request, responseObserver));
    }

    @Override
    public void getBlockByNum2(NumberMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getBlockByNum2(request, responseObserver));
    }

    @Override
    public void getDelegatedResource(DelegatedResourceMessage request,
        StreamObserver<DelegatedResourceList> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .getDelegatedResource(request, responseObserver));
    }

    @Override
    public void getDelegatedResourceAccountIndex(BytesMessage request,
        StreamObserver<org.bok.protos.Protocol.DelegatedResourceAccountIndex> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .getDelegatedResourceAccountIndex(request, responseObserver));
    }

    @Override
    public void getTransactionCountByBlockNum(NumberMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .getTransactionCountByBlockNum(request, responseObserver));
    }

    @Override
    public void getTransactionById(BytesMessage request,
        StreamObserver<Transaction> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getTransactionById(request, responseObserver));

    }

    @Override
    public void getTransactionInfoById(BytesMessage request,
        StreamObserver<TransactionInfo> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .getTransactionInfoById(request, responseObserver));

    }

    @Override
    public void listExchanges(EmptyMessage request, StreamObserver<ExchangeList> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().listExchanges(request, responseObserver));
    }

    @Override
    public void triggerConstantContract(TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .triggerConstantContract(request, responseObserver));
    }


    @Override
    public void generateAddress(EmptyMessage request,
        StreamObserver<AddressPrKeyPairMessage> responseObserver) {
      SignInterface cryptoEngine = SignUtils
          .getGeneratedRandomSign(Utils.getRandom(), Args.getInstance().isECKeyCryptoEngine());
      byte[] priKey = cryptoEngine.getPrivateKey();
      byte[] address = cryptoEngine.getAddress();
      String addressStr = StringUtil.encode58Check(address);
      String priKeyStr = Hex.encodeHexString(priKey);
      AddressPrKeyPairMessage.Builder builder = AddressPrKeyPairMessage.newBuilder();
      builder.setAddress(addressStr);
      builder.setPrivateKey(priKeyStr);
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getRewardInfo(BytesMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getRewardInfo(request, responseObserver));
    }

    @Override
    public void getBrokerageInfo(BytesMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().getBrokerageInfo(request, responseObserver));
    }

    @Override
    public void getMerkleTreeVoucherInfo(OutputPointInfo request,
        StreamObserver<IncrementalMerkleVoucherInfo> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .getMerkleTreeVoucherInfo(request, responseObserver));
    }

    @Override
    public void scanNoteByIvk(GrpcAPI.IvkDecryptParameters request,
        StreamObserver<GrpcAPI.DecryptNotes> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().scanNoteByIvk(request, responseObserver));
    }

    @Override
    public void scanAndMarkNoteByIvk(GrpcAPI.IvkDecryptAndMarkParameters request,
        StreamObserver<GrpcAPI.DecryptNotesMarked> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .scanAndMarkNoteByIvk(request, responseObserver));
    }

    @Override
    public void scanNoteByOvk(GrpcAPI.OvkDecryptParameters request,
        StreamObserver<GrpcAPI.DecryptNotes> responseObserver) {
      walletOnSolidity.futureGet(
          () -> rpcApiService.getWalletSolidityApi().scanNoteByOvk(request, responseObserver));
    }

    @Override
    public void isSpend(NoteParameters request, StreamObserver<SpendResult> responseObserver) {
      walletOnSolidity
          .futureGet(() -> rpcApiService.getWalletSolidityApi().isSpend(request, responseObserver));
    }

    @Override
    public void getTransactionInfoByBlockNum(NumberMessage request,
        StreamObserver<TransactionInfoList> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi()
          .getTransactionInfoByBlockNum(request, responseObserver));
    }
  }
}
