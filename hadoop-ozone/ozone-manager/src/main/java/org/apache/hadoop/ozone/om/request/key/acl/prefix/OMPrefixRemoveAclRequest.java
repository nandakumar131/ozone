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

package org.apache.hadoop.ozone.om.request.key.acl.prefix;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.ozone.OzoneAcl;
import org.apache.hadoop.ozone.OzoneConsts;
import org.apache.hadoop.ozone.audit.AuditLogger;
import org.apache.hadoop.ozone.audit.OMAction;
import org.apache.hadoop.ozone.om.OMMetrics;
import org.apache.hadoop.ozone.om.PrefixManagerImpl;
import org.apache.hadoop.ozone.om.PrefixManagerImpl.OMPrefixAclOpResult;
import org.apache.hadoop.ozone.om.helpers.OmPrefixInfo;
import org.apache.hadoop.ozone.om.request.util.OmResponseUtil;
import org.apache.hadoop.ozone.om.response.OMClientResponse;
import org.apache.hadoop.ozone.om.response.key.acl.prefix.OMPrefixAclResponse;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.OMRequest;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.OMResponse;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.RemoveAclResponse;
import org.apache.hadoop.ozone.security.acl.OzoneObj;
import org.apache.hadoop.ozone.security.acl.OzoneObjInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle remove Acl request for prefix.
 */
public class OMPrefixRemoveAclRequest extends OMPrefixAclRequest {

  private static final Logger LOG =
      LoggerFactory.getLogger(OMPrefixRemoveAclRequest.class);

  private final OzoneObj ozoneObj;
  private final List<OzoneAcl> ozoneAcls;

  public OMPrefixRemoveAclRequest(OMRequest omRequest) {
    super(omRequest);
    OzoneManagerProtocolProtos.RemoveAclRequest removeAclRequest =
        getOmRequest().getRemoveAclRequest();
    // TODO: conversion of OzoneObj to protobuf can be avoided when we have
    //  single code path for HA and Non-HA
    ozoneObj = OzoneObjInfo.fromProtobuf(removeAclRequest.getObj());
    ozoneAcls = Lists.newArrayList(
        OzoneAcl.fromProtobuf(removeAclRequest.getAcl()));
  }

  @Override
  OzoneObj getOzoneObj() {
    return ozoneObj;
  }

  @Override
  OMResponse.Builder onInit() {
    return OmResponseUtil.getOMResponseBuilder(getOmRequest());
  }

  @Override
  OMClientResponse onSuccess(OMResponse.Builder omResponse,
      OmPrefixInfo omPrefixInfo, boolean operationResult) {
    omResponse.setSuccess(operationResult);
    omResponse.setRemoveAclResponse(RemoveAclResponse.newBuilder()
        .setResponse(operationResult));
    return new OMPrefixAclResponse(omResponse.build(), omPrefixInfo);
  }

  @Override
  OMClientResponse onFailure(OMResponse.Builder omResponse,
      Exception exception) {
    return new OMPrefixAclResponse(createErrorOMResponse(omResponse,
        exception));
  }

  @Override
  void onComplete(OzoneObj resolvedOzoneObj, boolean operationResult,
      Exception exception, OMMetrics omMetrics, Result result, long trxnLogIndex,
      AuditLogger auditLogger, Map<String, String> auditMap) {
    switch (result) {
    case SUCCESS:
      if (LOG.isDebugEnabled()) {
        if (operationResult) {
          LOG.debug("Remove acl: {} to path: {} success!", ozoneAcls,
              resolvedOzoneObj.getPath());
        } else {
          LOG.debug("Acl {} not removed from path {} as it does not exist",
              ozoneAcls, resolvedOzoneObj.getPath());
        }
      }
      break;
    case FAILURE:
      LOG.error("Remove acl {} to path {} failed!", ozoneAcls,
          resolvedOzoneObj.getPath(), exception);
      break;
    default:
      LOG.error("Unrecognized Result for OMPrefixRemoveAclRequest: {}",
          getOmRequest());
    }

    if (ozoneAcls != null) {
      auditMap.put(OzoneConsts.ACL, ozoneAcls.toString());
    }
    markForAudit(auditLogger, buildAuditMessage(OMAction.REMOVE_ACL, auditMap,
        exception, getOmRequest().getUserInfo()));
  }

  @Override
  OMPrefixAclOpResult apply(OzoneObj resolvedOzoneObj, PrefixManagerImpl prefixManager,
      OmPrefixInfo omPrefixInfo, long trxnLogIndex) throws IOException {
    return prefixManager.removeAcl(resolvedOzoneObj, ozoneAcls.get(0), omPrefixInfo);
  }

}

