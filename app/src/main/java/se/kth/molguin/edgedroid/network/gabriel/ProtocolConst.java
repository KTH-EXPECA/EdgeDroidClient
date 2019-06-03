/**
 * Copyright 2019 Manuel Olguín
 * 
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
 */

package se.kth.molguin.edgedroid.network.gabriel;

public class ProtocolConst {
    // port protocol to the server
    //public static final int VIDEO_STREAM_PORT = 9098;
    // not used (yet):
    // public static final int ACC_STREAM_PORT = 9099;
    // public static final int AUDIO_STREAM_PORT = 9100;
    //public static final int RESULT_RECEIVING_PORT = 9111;
    //public static final int PORT = 22222;

    // token size
    //public static final int TOKEN_SIZE = 1;
    public static final String HEADER_MESSAGE_STATUS = "status";
    public static final String HEADER_MESSAGE_RESULT = "result";
    public static final String HEADER_MESSAGE_FRAME_ID = "frame_id";
    public static final String HEADER_MESSAGE_STATE_IDX = "state_index";
    public static final String HEADER_MESSAGE_SERVER_RECV = "ti";
    public static final String HEADER_MESSAGE_SERVER_SEND = "tf";

    public static final String STATUS_SUCCESS = "success";


    public static final String VIDEO_HEADER_FMT = "{\"frame_id\":%d}";
}
