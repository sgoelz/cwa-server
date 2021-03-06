/*
 * Corona-Warn-App
 *
 * SAP SE and all other contributors /
 * copyright owners license this file to you under the Apache
 * License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package app.coronawarn.server.services.distribution.assembly.diagnosiskeys.structure.file;

import app.coronawarn.server.common.persistence.domain.DiagnosisKey;
import app.coronawarn.server.common.protocols.external.exposurenotification.File;
import app.coronawarn.server.common.protocols.external.exposurenotification.Key;
import app.coronawarn.server.common.protocols.internal.FileBucket;
import app.coronawarn.server.services.distribution.assembly.diagnosiskeys.util.Batch;
import app.coronawarn.server.services.distribution.assembly.diagnosiskeys.util.DateTime;
import app.coronawarn.server.services.distribution.assembly.structure.file.FileImpl;
import app.coronawarn.server.services.distribution.assembly.structure.util.ImmutableStack;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HourFileImpl extends FileImpl {

  private static final Logger logger = LoggerFactory.getLogger(HourFileImpl.class);

  private static final String INDEX_FILE_NAME = "index";

  private final LocalDateTime currentHour;
  private final String region;
  private final Collection<DiagnosisKey> diagnosisKeys;

  /**
   * Constructs an {@link HourFileImpl} instance that is associated with the specified region and hour. The specified
   * {@link DiagnosisKey} collection is filtered by the specified hour.
   *
   * @param currentHour   The hour for which diagnosis keys will be aggregated.
   * @param region        The region associated with this instance.
   * @param diagnosisKeys The {@link DiagnosisKey} collection that will be filtered by the specified hour.
   */
  public HourFileImpl(LocalDateTime currentHour, String region,
      Collection<DiagnosisKey> diagnosisKeys) {
    super(INDEX_FILE_NAME, new byte[0]);
    this.currentHour = currentHour;
    this.region = region;
    this.diagnosisKeys = diagnosisKeys;
  }

  @Override
  public void prepare(ImmutableStack<Object> indices) {
    this.setBytes(createHourBytes());
    super.prepare(indices);
  }

  private byte[] createHourBytes() {
    logger.debug("Creating hour file for {}", currentHour);
    return FileBucket.newBuilder()
        .addAllFiles(generateFiles(diagnosisKeys, currentHour, region))
        .build()
        .toByteArray();
  }

  private static Set<File> generateFiles(Collection<DiagnosisKey> diagnosisKeys,
      LocalDateTime currentHour, String region) {
    Instant startTimestamp = Instant.from(currentHour.atOffset(ZoneOffset.UTC));
    Instant endTimestamp = Instant.from(currentHour.atOffset(ZoneOffset.UTC).plusHours(1));
    Set<Key> keys = createKeys(diagnosisKeys, currentHour);
    return Batch.aggregateKeys(keys, startTimestamp, endTimestamp, region);
  }

  private static Set<Key> createKeys(Collection<DiagnosisKey> diagnosisKeys,
      LocalDateTime currentHour) {
    return diagnosisKeys.stream()
        .filter(diagnosisKey -> DateTime
            .getLocalDateTimeFromHoursSinceEpoch(diagnosisKey.getSubmissionTimestamp())
            .equals(currentHour))
        .map(HourFileImpl::createKey)
        .collect(Collectors.toSet());
  }

  private static Key createKey(DiagnosisKey key) {
    return Key.newBuilder()
        .setKeyData(ByteString.copyFrom(key.getKeyData()))
        .setRollingStartNumber(Math.toIntExact(key.getRollingStartNumber()))
        .setRollingPeriod(Math.toIntExact(key.getRollingPeriod()))
        .setTransmissionRiskLevel(key.getTransmissionRiskLevel())
        .build();
  }
}
