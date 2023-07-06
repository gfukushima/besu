/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageFormat.BONSAI;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class WorldStateProofProviderTest {

  private static final Address address =
      Address.fromHexString("0x1234567890123456789012345678901234567890");

  private WorldStateStorage worldStateStorage;
  private WorldStateProofProvider worldStateProofProvider;

  private static Stream<Arguments> getDataStorageFormat() {
    return Stream.of(Arguments.of(BONSAI), Arguments.of(DataStorageFormat.FOREST));
  }

  public void setup(final DataStorageFormat dataStorageFormat) {
    if (dataStorageFormat.equals(BONSAI)) {
      worldStateStorage =
          new BonsaiWorldStateKeyValueStorage(
              new InMemoryKeyValueStorageProvider(), new NoOpMetricsSystem());
    } else {
      worldStateStorage = new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    }

    worldStateProofProvider = new WorldStateProofProvider(worldStateStorage);
  }

  @ParameterizedTest
  @MethodSource("getDataStorageFormat")
  public void getProofWhenWorldStateNotAvailable(final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    Optional<WorldStateProof> accountProof =
        worldStateProofProvider.getAccountProof(Hash.EMPTY, address, new ArrayList<>());

    assertThat(accountProof).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("getDataStorageFormat")
  public void getProofWhenWorldStateAvailable(final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    final Hash addressHash = Hash.hash(address);
    final MerkleTrie<Bytes32, Bytes> worldStateTrie = emptyWorldStateTrie(addressHash);
    final MerkleTrie<Bytes32, Bytes> storageTrie = emptyStorageTrie();

    final WorldStateStorage.Updater updater = worldStateStorage.updater();

    // Add some storage values
    writeStorageValue(storageTrie, UInt256.ONE, UInt256.valueOf(2L));
    writeStorageValue(storageTrie, UInt256.valueOf(2L), UInt256.valueOf(4L));
    writeStorageValue(storageTrie, UInt256.valueOf(3L), UInt256.valueOf(6L));
    // Save to Storage
    storageTrie.commit(
        (location, hash, value) ->
            updater.putAccountStorageTrieNode(addressHash, location, hash, value));

    // Define account value
    final Hash codeHash = Hash.hash(Bytes.fromHexString("0x1122"));
    final StateTrieAccountValue accountValue =
        new StateTrieAccountValue(1L, Wei.of(2L), Hash.wrap(storageTrie.getRootHash()), codeHash);
    // Save to storage
    worldStateTrie.put(addressHash, RLP.encode(accountValue::writeTo));
    worldStateTrie.commit(
        (location, hash, value) -> {
          updater.putAccountStateTrieNode(location, hash, value);
          if (location.isEmpty()) {
            updater.saveWorldState(Bytes.EMPTY, worldStateTrie.getRootHash(), value);
          }
        });
    // Persist updates
    updater.commit();

    final List<UInt256> storageKeys =
        Arrays.asList(UInt256.ONE, UInt256.valueOf(3L), UInt256.valueOf(6L));
    final Optional<WorldStateProof> accountProof =
        worldStateProofProvider.getAccountProof(
            Hash.wrap(worldStateTrie.getRootHash()), address, storageKeys);

    assertThat(accountProof).isPresent();
    Assertions.assertThat(accountProof.get().getStateTrieAccountValue())
        .isEqualToComparingFieldByField(accountValue);
    assertThat(accountProof.get().getAccountProof().size()).isGreaterThanOrEqualTo(1);
    // Check storage fields
    assertThat(accountProof.get().getStorageKeys()).isEqualTo(storageKeys);
    // Check key 1
    UInt256 storageKey = UInt256.ONE;
    assertThat(accountProof.get().getStorageValue(storageKey)).isEqualTo(UInt256.valueOf(2L));
    assertThat(accountProof.get().getStorageProof(storageKey).size()).isGreaterThanOrEqualTo(1);
    // Check key 3
    storageKey = UInt256.valueOf(3L);
    assertThat(accountProof.get().getStorageValue(storageKey)).isEqualTo(UInt256.valueOf(6L));
    assertThat(accountProof.get().getStorageProof(storageKey).size()).isGreaterThanOrEqualTo(1);
    // Check key 6
    storageKey = UInt256.valueOf(6L);
    assertThat(accountProof.get().getStorageValue(storageKey)).isEqualTo(UInt256.ZERO);
    assertThat(accountProof.get().getStorageProof(storageKey).size()).isGreaterThanOrEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("getDataStorageFormat")
  public void getProofWhenStateTrieAccountUnavailable(final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    final MerkleTrie<Bytes32, Bytes> worldStateTrie = emptyWorldStateTrie(null);

    final Optional<WorldStateProof> accountProof =
        worldStateProofProvider.getAccountProof(
            Hash.wrap(worldStateTrie.getRootHash()), address, new ArrayList<>());

    assertThat(accountProof).isEmpty();
  }

  private void writeStorageValue(
      final MerkleTrie<Bytes32, Bytes> storageTrie, final UInt256 key, final UInt256 value) {
    storageTrie.put(storageKeyHash(key), encodeStorageValue(value));
  }

  private Bytes32 storageKeyHash(final UInt256 storageKey) {
    return Hash.hash(storageKey);
  }

  private Bytes encodeStorageValue(final UInt256 storageValue) {
    return RLP.encode(out -> out.writeBytes(storageValue.toMinimalBytes()));
  }

  private MerkleTrie<Bytes32, Bytes> emptyStorageTrie() {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode, b -> b, b -> b);
  }

  private MerkleTrie<Bytes32, Bytes> emptyWorldStateTrie(final Hash accountHash) {
    return new StoredMerklePatriciaTrie<>(
        (location, hash) ->
            worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
        b -> b,
        b -> b);
  }
}
