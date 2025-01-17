package org.bok.core.db2;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.bok.common.application.Application;
import org.bok.common.application.ApplicationFactory;
import org.bok.common.application.UnApplicationContext;
import org.bok.common.utils.FileUtil;
import org.bok.common.utils.SessionOptional;
import org.bok.core.Constant;
import org.bok.core.config.DefaultConfig;
import org.bok.core.config.args.Args;
import org.bok.core.db.UnStoreWithRevoking;
import org.bok.core.db2.SnapshotRootTest.ProtoCapsuleTest;
import org.bok.core.db2.core.SnapshotManager;
import org.bok.core.exception.RevokingStoreIllegalStateException;

@Slf4j
public class RevokingDbWithCacheNewValueTest {

  private SnapshotManager revokingDatabase;
  private UnApplicationContext context;
  private Application appT;
  private TestRevokingUnStore unDatabase;

  @Before
  public void init() {
    Args.setParam(new String[]{"-d", "output_revokingStore_test"},
        Constant.TEST_CONF);
    context = new UnApplicationContext(DefaultConfig.class);
    appT = ApplicationFactory.create(context);
  }

  @After
  public void removeDb() {
    Args.clearParam();
    context.destroy();
    unDatabase.close();
    FileUtil.deleteDir(new File("output_revokingStore_test"));
  }

  @Test
  public synchronized void testPop() throws RevokingStoreIllegalStateException {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unDatabase = new TestRevokingUnStore("testRevokingDBWithCacheNewValue-testPop");
    revokingDatabase.add(unDatabase.getRevokingDB());

    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    for (int i = 1; i < 11; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("pop" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        unDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        Assert.assertEquals(1, revokingDatabase.getActiveSession());
        tmpSession.commit();
        Assert.assertEquals(i, revokingDatabase.getSize());
        Assert.assertEquals(0, revokingDatabase.getActiveSession());
      }
    }

    for (int i = 1; i < 11; i++) {
      revokingDatabase.pop();
      Assert.assertEquals(10 - i, revokingDatabase.getSize());
    }

    Assert.assertEquals(0, revokingDatabase.getSize());
  }

  @Test
  public synchronized void testMerge() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unDatabase = new TestRevokingUnStore("testRevokingDBWithCacheNewValue-testMerge");
    revokingDatabase.add(unDatabase.getRevokingDB());

    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }
    SessionOptional dialog = SessionOptional.instance().setValue(revokingDatabase.buildSession());
    dialog.setValue(revokingDatabase.buildSession());
    ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("merge".getBytes());
    ProtoCapsuleTest testProtoCapsule2 = new ProtoCapsuleTest("merge2".getBytes());

    unDatabase.put(testProtoCapsule.getData(), testProtoCapsule);

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unDatabase.put(testProtoCapsule.getData(), testProtoCapsule2);
      tmpSession.merge();
    }
    Assert.assertEquals(testProtoCapsule2, unDatabase.get(testProtoCapsule.getData()));

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unDatabase.delete(testProtoCapsule.getData());
      tmpSession.merge();
    }
    Assert.assertEquals(null, unDatabase.get(testProtoCapsule.getData()));
    dialog.reset();
  }


  @Test
  public synchronized void testRevoke() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unDatabase = new TestRevokingUnStore("testRevokingDBWithCacheNewValue-testRevoke");
    revokingDatabase.add(unDatabase.getRevokingDB());

    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }
    SessionOptional dialog = SessionOptional.instance().setValue(revokingDatabase.buildSession());
    for (int i = 0; i < 10; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("undo" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        unDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        Assert.assertEquals(2, revokingDatabase.getSize());
        tmpSession.merge();
        Assert.assertEquals(1, revokingDatabase.getSize());
      }
    }

    Assert.assertEquals(1, revokingDatabase.getSize());
    dialog.reset();
    Assert.assertTrue(revokingDatabase.getSize() == 0);
    Assert.assertEquals(0, revokingDatabase.getActiveSession());

    dialog.setValue(revokingDatabase.buildSession());
    ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("revoke".getBytes());
    ProtoCapsuleTest testProtoCapsule2 = new ProtoCapsuleTest("revoke2".getBytes());
    unDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
    dialog.setValue(revokingDatabase.buildSession());

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unDatabase.put(testProtoCapsule.getData(), testProtoCapsule2);
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unDatabase.put(testProtoCapsule.getData(), new ProtoCapsuleTest("revoke22".getBytes()));
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unDatabase.put(testProtoCapsule.getData(), new ProtoCapsuleTest("revoke222".getBytes()));
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unDatabase.delete(testProtoCapsule.getData());
      tmpSession.merge();
    }

    dialog.reset();

    logger.info("**********testProtoCapsule:" + String
        .valueOf(unDatabase.getUnchecked(testProtoCapsule.getData())));
    Assert.assertEquals(testProtoCapsule, unDatabase.get(testProtoCapsule.getData()));
  }

  @Test
  public synchronized void testGetlatestValues() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unDatabase = new TestRevokingUnStore("testSnapshotManager-testGetlatestValues");
    revokingDatabase.add(unDatabase.getRevokingDB());
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    for (int i = 1; i < 10; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("getLastestValues" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        unDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }

    Set<ProtoCapsuleTest> result = unDatabase.getRevokingDB().getlatestValues(5).stream()
        .map(ProtoCapsuleTest::new)
        .collect(Collectors.toSet());

    for (int i = 9; i >= 5; i--) {
      Assert.assertEquals(true,
          result.contains(new ProtoCapsuleTest(("getLastestValues" + i).getBytes())));
    }
  }

  @Test
  public synchronized void testGetValuesNext() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unDatabase = new TestRevokingUnStore("testSnapshotManager-testGetValuesNext");
    revokingDatabase.add(unDatabase.getRevokingDB());
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    for (int i = 1; i < 10; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("getValuesNext" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        unDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }

    Set<ProtoCapsuleTest> result =
        unDatabase.getRevokingDB().getValuesNext(
            new ProtoCapsuleTest("getValuesNext2".getBytes()).getData(), 3
        ).stream().map(ProtoCapsuleTest::new).collect(Collectors.toSet());

    for (int i = 2; i < 5; i++) {
      Assert.assertEquals(true,
          result.contains(new ProtoCapsuleTest(("getValuesNext" + i).getBytes())));
    }
  }


  public static class TestRevokingUnStore extends UnStoreWithRevoking<ProtoCapsuleTest> {

    protected TestRevokingUnStore(String dbName) {
      super(dbName);
    }

    @Override
    public ProtoCapsuleTest get(byte[] key) {
      byte[] value = this.revokingDB.getUnchecked(key);
      return ArrayUtils.isEmpty(value) ? null : new ProtoCapsuleTest(value);
    }
  }

  public static class TestSnapshotManager extends SnapshotManager {

    public TestSnapshotManager(String checkpointPath) {
      super(checkpointPath);
    }
  }
}
