/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.cli.subcommands.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper class for counting and pruning trie logs */
public class TrieLogHelper {
  private static final String TRIE_LOG_FILE = "trieLogsToRetain";
  private static final long BATCH_SIZE = 20000;
  private static final int ROCKSDB_MAX_INSERTS_PER_TRANSACTION = 1000;
  private static final Logger LOG = LoggerFactory.getLogger(TrieLogHelper.class);

  static void prune(
      final DataStorageConfiguration config,
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final MutableBlockchain blockchain) {

    TrieLogHelper.validatePruneConfiguration(config);

    final long layersToRetain = config.getUnstable().getBonsaiTrieLogRetentionThreshold();

    final long chainHeight = blockchain.getChainHeadBlockNumber();

    final long lastBlockNumberToRetainTrieLogsFor = chainHeight - layersToRetain;

    if (!validPruneRequirements(blockchain, chainHeight, lastBlockNumberToRetainTrieLogsFor)) {
      return;
    }

    final long numberOfBatches = calculateNumberofBatches(layersToRetain);

    processTrieLogBatches(
        rootWorldStateStorage,
        blockchain,
        chainHeight,
        lastBlockNumberToRetainTrieLogsFor,
        numberOfBatches);

    if (rootWorldStateStorage.streamTrieLogKeys(layersToRetain).count() == layersToRetain) {
      deleteFiles(numberOfBatches);
    } else {
      LOG.error("Prune failed. Re-run the subcommand to load the trie logs from file.");
    }
  }

  private static void processTrieLogBatches(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final MutableBlockchain blockchain,
      final long chainHeight,
      final long lastBlockNumberToRetainTrieLogsFor,
      final long numberOfBatches) {

    for (long batchNumber = 1; batchNumber <= numberOfBatches; batchNumber++) {
      saveTrieLogBatches(
          rootWorldStateStorage,
          blockchain,
          chainHeight,
          lastBlockNumberToRetainTrieLogsFor,
          batchNumber);
    }

    LOG.info("Clear trie logs...");
    rootWorldStateStorage.clearTrieLog();

    for (long batchNumber = 1; batchNumber <= numberOfBatches; batchNumber++) {
      restoreTrieLogBatches(rootWorldStateStorage, batchNumber);
    }
  }

  private static void saveTrieLogBatches(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final MutableBlockchain blockchain,
      final long chainHeight,
      final long lastBlockNumberToRetainTrieLogsFor,
      final long batchNumber) {

    final long firstBlockOfBatch = chainHeight - ((batchNumber - 1) * BATCH_SIZE);

    final long lastBlockOfBatch =
        Math.max(chainHeight - (batchNumber * BATCH_SIZE), lastBlockNumberToRetainTrieLogsFor);

    final List<Hash> trieLogKeys = new ArrayList<>();

    getTrieLogKeysForBlocks(blockchain, firstBlockOfBatch, lastBlockOfBatch, trieLogKeys);

    LOG.info("Saving trie logs to retain in file (batch {})...", batchNumber);

    try {
      saveTrieLogsInFile(trieLogKeys, rootWorldStateStorage, batchNumber);
    } catch (IOException e) {
      LOG.error("Error saving trie logs to file: {}", e.getMessage());
    }
  }

  private static void restoreTrieLogBatches(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage, final long batchNumber) {

    try {
      LOG.info("Restoring trie logs retained from batch {}...", batchNumber);
      recreateTrieLogs(rootWorldStateStorage, batchNumber);
    } catch (IOException | ClassNotFoundException e) {
      LOG.error("Error recreating trie logs from batch {}: {}", batchNumber, e.getMessage());
    }
  }

  private static void deleteFiles(final long numberOfBatches) {

    LOG.info("Deleting files...");

    for (long batchNumber = 1; batchNumber <= numberOfBatches; batchNumber++) {

      deleteTrieLogFile(batchNumber);
    }

    LOG.info("Prune ran successfully. Enjoy some disk space back! \uD83D\uDE80");
  }

  private static void getTrieLogKeysForBlocks(
      final MutableBlockchain blockchain,
      final long firstBlockOfBatch,
      final long lastBlockOfBatch,
      final List<Hash> trieLogKeys) {
    for (long i = firstBlockOfBatch; i > lastBlockOfBatch; i--) {
      final Optional<BlockHeader> header = blockchain.getBlockHeader(i);
      header.ifPresentOrElse(
          blockHeader -> trieLogKeys.add(blockHeader.getHash()),
          () -> LOG.error("Error retrieving block"));
    }
  }

  private static long calculateNumberofBatches(final long layersToRetain) {
    return layersToRetain / BATCH_SIZE + ((layersToRetain % BATCH_SIZE == 0) ? 0 : 1);
  }

  private static boolean validPruneRequirements(
      final MutableBlockchain blockchain,
      final long chainHeight,
      final long lastBlockNumberToRetainTrieLogsFor) {
    if (lastBlockNumberToRetainTrieLogsFor < 0) {
      LOG.error(
          "Trying to retain more trie logs than chain height ({}), skipping pruning", chainHeight);
      return false;
    }

    final Optional<Hash> finalizedBlockHash = blockchain.getFinalized();

    if (finalizedBlockHash.isEmpty()) {
      LOG.error("No finalized block present, skipping pruning");
      return false;
    } else {
      final Hash finalizedHash = finalizedBlockHash.get();
      if (blockchain.getBlockHeader(finalizedHash).isPresent()
          && blockchain.getBlockHeader(finalizedHash).get().getNumber()
              < lastBlockNumberToRetainTrieLogsFor) {
        LOG.error("Trying to prune more layers than the finalized block height, skipping pruning");
        return false;
      }
    }
    return true;
  }

  private static void recreateTrieLogs(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage, final long batchNumber)
      throws IOException, ClassNotFoundException {
    // process in chunk to avoid OOM

    IdentityHashMap<byte[], byte[]> trieLogsToRetain = readTrieLogsFromFile(batchNumber);
    final int chunkSize = ROCKSDB_MAX_INSERTS_PER_TRANSACTION;
    List<byte[]> keys = new ArrayList<>(trieLogsToRetain.keySet());

    for (int startIndex = 0; startIndex < keys.size(); startIndex += chunkSize) {
      processTransactionChunk(startIndex, chunkSize, keys, trieLogsToRetain, rootWorldStateStorage);
    }
  }

  private static void processTransactionChunk(
      final int startIndex,
      final int chunkSize,
      final List<byte[]> keys,
      final IdentityHashMap<byte[], byte[]> trieLogsToRetain,
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage) {

    var updater = rootWorldStateStorage.updater();
    int endIndex = Math.min(startIndex + chunkSize, keys.size());

    for (int i = startIndex; i < endIndex; i++) {
      byte[] key = keys.get(i);
      byte[] value = trieLogsToRetain.get(key);
      updater.getTrieLogStorageTransaction().put(key, value);
      LOG.info("Key({}): {}", i, Bytes32.wrap(key).toShortHexString());
    }

    updater.getTrieLogStorageTransaction().commit();
  }

  private static void validatePruneConfiguration(final DataStorageConfiguration config) {
    checkArgument(
        config.getUnstable().getBonsaiTrieLogRetentionThreshold()
            >= MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD,
        String.format(
            "--Xbonsai-trie-log-retention-threshold minimum value is %d",
            MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD));
    checkArgument(
        config.getUnstable().getBonsaiTrieLogPruningLimit() > 0,
        String.format(
            "--Xbonsai-trie-log-pruning-limit=%d must be greater than 0",
            config.getUnstable().getBonsaiTrieLogPruningLimit()));
    checkArgument(
        config.getUnstable().getBonsaiTrieLogPruningLimit()
            > config.getUnstable().getBonsaiTrieLogRetentionThreshold(),
        String.format(
            "--Xbonsai-trie-log-pruning-limit=%d must greater than --Xbonsai-trie-log-retention-threshold=%d",
            config.getUnstable().getBonsaiTrieLogPruningLimit(),
            config.getUnstable().getBonsaiTrieLogRetentionThreshold()));
  }

  static TrieLogCount getCount(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final int limit,
      final Blockchain blockchain) {
    final AtomicInteger total = new AtomicInteger();
    final AtomicInteger canonicalCount = new AtomicInteger();
    final AtomicInteger forkCount = new AtomicInteger();
    final AtomicInteger orphanCount = new AtomicInteger();
    rootWorldStateStorage
        .streamTrieLogKeys(limit)
        .map(Bytes32::wrap)
        .map(Hash::wrap)
        .forEach(
            hash -> {
              total.getAndIncrement();
              blockchain
                  .getBlockHeader(hash)
                  .ifPresentOrElse(
                      (header) -> {
                        long number = header.getNumber();
                        final Optional<BlockHeader> headerByNumber =
                            blockchain.getBlockHeader(number);
                        if (headerByNumber.isPresent()
                            && headerByNumber.get().getHash().equals(hash)) {
                          canonicalCount.getAndIncrement();
                        } else {
                          forkCount.getAndIncrement();
                        }
                      },
                      orphanCount::getAndIncrement);
            });

    return new TrieLogCount(total.get(), canonicalCount.get(), forkCount.get(), orphanCount.get());
  }

  private static void saveTrieLogsInFile(
      final List<Hash> trieLogs,
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final long batchNumber)
      throws IOException {

    File file = new File(TRIE_LOG_FILE + "-" + batchNumber);
    if (file.exists()) {
      LOG.error("File already exists, skipping file creation");
      return;
    }
    try (FileOutputStream fos = new FileOutputStream(file)) {
      try {
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(getTrieLogs(trieLogs, rootWorldStateStorage));
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    } catch (IOException e) {
      LOG.error(e.getMessage());
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private static IdentityHashMap<byte[], byte[]> readTrieLogsFromFile(final long batchNumber)
      throws IOException, ClassNotFoundException {

    IdentityHashMap<byte[], byte[]> trieLogs;
    try (FileInputStream fis = new FileInputStream(TRIE_LOG_FILE + "-" + batchNumber);
        ObjectInputStream ois = new ObjectInputStream(fis)) {

      trieLogs = (IdentityHashMap<byte[], byte[]>) ois.readObject();

    } catch (IOException | ClassNotFoundException e) {

      LOG.error(e.getMessage());
      throw e;
    }

    return trieLogs;
  }

  private static IdentityHashMap<byte[], byte[]> getTrieLogs(
      final List<Hash> trieLogKeys, final BonsaiWorldStateKeyValueStorage rootWorldStateStorage) {
    IdentityHashMap<byte[], byte[]> trieLogsToRetain = new IdentityHashMap<>();

    LOG.info("Obtaining trielogs from db, this may take a few minutes...");
    trieLogKeys.forEach(
        hash ->
            rootWorldStateStorage
                .getTrieLog(hash)
                .ifPresent(trieLog -> trieLogsToRetain.put(hash.toArrayUnsafe(), trieLog)));
    return trieLogsToRetain;
  }

  private static void deleteTrieLogFile(final long batchNumber) {
    File file = new File(TRIE_LOG_FILE + "-" + batchNumber);
    if (file.exists()) {
      file.delete();
    }
  }

  static void printCount(final PrintWriter out, final TrieLogCount count) {
    out.printf(
        "trieLog count: %s\n - canonical count: %s\n - fork count: %s\n - orphaned count: %s\n",
        count.total, count.canonicalCount, count.forkCount, count.orphanCount);
  }

  record TrieLogCount(int total, int canonicalCount, int forkCount, int orphanCount) {}
}
