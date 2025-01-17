package org.bok.core.actuator.utils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.bok.common.application.Application;
import org.bok.common.application.ApplicationFactory;
import org.bok.common.application.UnApplicationContext;
import org.bok.common.utils.FileUtil;
import org.bok.core.Constant;
import org.bok.core.config.DefaultConfig;
import org.bok.core.config.args.Args;
import org.bok.core.utils.TransactionUtil;


@Slf4j(topic = "capsule")
public class TransactionUtilTest {

  private static final String dbPath = "output_transactionUtil_test";
  public static Application AppT;
  private static UnApplicationContext context;

  /**
   * Init .
   */
  @BeforeClass
  public static void init() {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new UnApplicationContext(DefaultConfig.class);
    AppT = ApplicationFactory.create(context);
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  @Test
  public void validAccountNameCheck() throws UnsupportedEncodingException {
    TransactionUtil actuatorUtil = new TransactionUtil();
    String account = "";
    Assert.assertEquals(true, actuatorUtil.validAccountName(account.getBytes("utf-8")));
    for (int i = 0; i < 200; i++) {
      account += (char) ('a' + (i % 26));
    }
    Assert.assertEquals(true, actuatorUtil.validAccountName(account.getBytes("utf-8")));
    account += 'z';
    Assert.assertEquals(false, actuatorUtil.validAccountName(account.getBytes("utf-8")));

  }

  @Test
  public void validAccountIdCheck() throws UnsupportedEncodingException {
    TransactionUtil actuatorUtil = new TransactionUtil();
    String accountId = "";
    Assert.assertEquals(false, actuatorUtil.validAccountId(accountId.getBytes("utf-8")));
    for (int i = 0; i < 7; i++) {
      accountId += (char) ('a' + (i % 26));
    }
    Assert.assertEquals(false, actuatorUtil.validAccountId(accountId.getBytes("utf-8")));
    for (int i = 0; i < 26; i++) {
      accountId += (char) ('a' + (i % 26));
    }
    Assert.assertEquals(false, actuatorUtil.validAccountId(accountId.getBytes("utf-8")));
    accountId = "ab  cdefghij";
    Assert.assertEquals(false, actuatorUtil.validAccountId(accountId.getBytes("utf-8")));
    accountId = Character.toString((char) 128) + "abcdefjijk" + Character.toString((char) 129);
    Assert.assertEquals(false, actuatorUtil.validAccountId(accountId.getBytes("utf-8")));
    accountId = "";
    for (int i = 0; i < 30; i++) {
      accountId += (char) ('a' + (i % 26));
    }
    Assert.assertEquals(true, actuatorUtil.validAccountId(accountId.getBytes("utf-8")));

  }

  @Test
  public void validAssetNameCheck() throws UnsupportedEncodingException {
    TransactionUtil actuatorUtil = new TransactionUtil();
    String assetName = "";
    Assert.assertEquals(false, actuatorUtil.validAssetName(assetName.getBytes("utf-8")));
    for (int i = 0; i < 33; i++) {
      assetName += (char) ('a' + (i % 26));
    }
    Assert.assertEquals(false, actuatorUtil.validAssetName(assetName.getBytes("utf-8")));
    assetName = "ab  cdefghij";
    Assert.assertEquals(false, actuatorUtil.validAssetName(assetName.getBytes("utf-8")));
    assetName = Character.toString((char) 128) + "abcdefjijk" + Character.toString((char) 129);
    Assert.assertEquals(false, actuatorUtil.validAssetName(assetName.getBytes("utf-8")));
    assetName = "";
    for (int i = 0; i < 20; i++) {
      assetName += (char) ('a' + (i % 26));
    }
    Assert.assertEquals(true, actuatorUtil.validAssetName(assetName.getBytes("utf-8")));
  }

  @Test
  public void validTokenAbbrNameCheck() throws UnsupportedEncodingException {

    TransactionUtil actuatorUtil = new TransactionUtil();
    String abbrName = "";
    Assert.assertEquals(false, actuatorUtil.validTokenAbbrName(abbrName.getBytes("utf-8")));
    for (int i = 0; i < 6; i++) {
      abbrName += (char) ('a' + (i % 26));
    }
    Assert.assertEquals(false, actuatorUtil.validTokenAbbrName(abbrName.getBytes("utf-8")));
    abbrName = "a bd";
    Assert.assertEquals(false, actuatorUtil.validTokenAbbrName(abbrName.getBytes("utf-8")));
    abbrName = "a" + Character.toString((char) 129) + 'f';
    Assert.assertEquals(false, actuatorUtil.validTokenAbbrName(abbrName.getBytes("utf-8")));
    abbrName = "";
    for (int i = 0; i < 5; i++) {
      abbrName += (char) ('a' + (i % 26));
    }
    Assert.assertEquals(true, actuatorUtil.validTokenAbbrName(abbrName.getBytes("utf-8")));
  }

  @Test
  public void isNumberCheck() throws UnsupportedEncodingException {
    TransactionUtil actuatorUtil = new TransactionUtil();
    String number = "";
    Assert.assertEquals(false, actuatorUtil.isNumber(number.getBytes("utf-8")));

    number = "123df34";
    Assert.assertEquals(false, actuatorUtil.isNumber(number.getBytes("utf-8")));
    number = "013";
    Assert.assertEquals(false, actuatorUtil.isNumber(number.getBytes("utf-8")));
    number = "24";
    Assert.assertEquals(true, actuatorUtil.isNumber(number.getBytes("utf-8")));
  }

}
