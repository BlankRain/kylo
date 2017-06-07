package com.thinkbiganalytics.nifi.provenance.cache;

/*-
 * #%L
 * thinkbig-nifi-provenance-repo
 * %%
 * Copyright (C) 2017 ThinkBig Analytics
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.thinkbiganalytics.nifi.provenance.jms.ProvenanceEventActiveMqWriter;
import com.thinkbiganalytics.nifi.provenance.model.FeedFlowFile;
import com.thinkbiganalytics.nifi.provenance.model.ProvenanceEventRecordDTO;
import com.thinkbiganalytics.nifi.provenance.model.ProvenanceEventRecordDTOHolder;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

/**
 * Created by sr186054 on 6/1/17.
 */
public class FeedFlowFileExpireListener implements FeedFlowFileCacheListener {

    @Autowired
    private ProvenanceEventActiveMqWriter provenanceEventActiveMqWriter;

    @Autowired
    private FeedFlowFileGuavaCache cache;

    public FeedFlowFileExpireListener() {

    }

    @PostConstruct
    private void init(){
       cache.subscribe(this);
    }

    @Override
    public void onInvalidate(FeedFlowFile flowFile) {

    }

    public void beforeInvalidation(List<FeedFlowFile> completedFlowFiles) {
        List<ProvenanceEventRecordDTO> updatedEvents = null;
        List<FeedFlowFile> filesToProcess = completedFlowFiles.stream().filter(feedFlowFile -> feedFlowFile.getPrimaryRelatedBatchFeedFlow() != null && feedFlowFile.getId().equalsIgnoreCase(feedFlowFile.getPrimaryRelatedBatchFeedFlow())).collect(Collectors.toList());

           for(FeedFlowFile feedFlowFile : filesToProcess){
            List<ProvenanceEventRecordDTO> dirtyEvents =  feedFlowFile.getFeedFlowFileJobTrackingStats().getUpdatedProvenanceEvents();
            if(dirtyEvents != null && !dirtyEvents.isEmpty()) {
                if(updatedEvents == null){
                    updatedEvents = new ArrayList<>();
                }
                updatedEvents.addAll(dirtyEvents);
            }
        }
        if(updatedEvents != null && !updatedEvents.isEmpty()) {
            ProvenanceEventRecordDTOHolder eventRecordDTOHolder = new ProvenanceEventRecordDTOHolder();
            eventRecordDTOHolder.setEvents(updatedEvents);
            provenanceEventActiveMqWriter.writeBatchEvents(eventRecordDTOHolder);
        }
    }
}