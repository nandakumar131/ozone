/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.ozone.recon.tasks;

import com.google.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.ozone.om.OMMetadataManager;
import org.apache.hadoop.ozone.om.OmMetadataManagerImpl;
import org.apache.hadoop.ozone.om.helpers.BucketLayout;
import org.hadoop.ozone.recon.schema.UtilizationSchemaDefinition;
import org.hadoop.ozone.recon.schema.tables.daos.FileCountBySizeDao;
import org.jooq.DSLContext;

/**
 * Task for ObjectStore (OBS) which processes the KEY_TABLE.
 */
public class FileSizeCountTaskOBS implements ReconOmTask {

  private final FileCountBySizeDao fileCountBySizeDao;
  private final DSLContext dslContext;

  @Inject
  public FileSizeCountTaskOBS(FileCountBySizeDao fileCountBySizeDao,
                              UtilizationSchemaDefinition utilizationSchemaDefinition) {
    this.fileCountBySizeDao = fileCountBySizeDao;
    this.dslContext = utilizationSchemaDefinition.getDSLContext();
  }

  @Override
  public Pair<String, Boolean> reprocess(OMMetadataManager omMetadataManager) {
    return FileSizeCountTaskHelper.reprocess(
        omMetadataManager,
        dslContext,
        fileCountBySizeDao,
        BucketLayout.OBJECT_STORE,
        getTaskName()
    );
  }

  @Override
  public Pair<String, Boolean> process(OMUpdateEventBatch events) {
    // This task listens only on the KEY_TABLE.
    return FileSizeCountTaskHelper.processEvents(
        events,
        OmMetadataManagerImpl.KEY_TABLE,
        dslContext,
        fileCountBySizeDao,
        getTaskName());
  }

  @Override
  public String getTaskName() {
    return "FileSizeCountTaskOBS";
  }
}
