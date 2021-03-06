# Copyright 2010 Orbitz WorldWide
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Modified by Mikko Korpela under NSN copyrights
#  Copyright 2008-2012 Nokia Siemens Networks Oyj
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Ammended by Timothy Alexander <dragonfyre13@gmail.com>
# (StreamHandler class added)
#   Copyright 2013 Timothy Alexander
#   Licensed under the Apache License, Version 2.0
#      http://www.apache.org/licenses/LICENSE-2.0

#
# Modified by Mateusz Marzec under NSN copyrights
# Copyright 2015 Nokia Solutions and Networks
# * Licensed under the Apache License, Version 2.0,
# * see license.txt file for details.
#


'''A Robot Framework listener that sends information to a socket'''

import os
import sys
import socket
import threading
import inspect
import copy
import json
import time

if sys.version_info < (3, 0, 0):
    import SocketServer as socketserver
else:
    import socketserver

from robot.running.signalhandler import STOP_SIGNAL_MONITOR
from robot.errors import ExecutionFailed


# Setting Output encoding to UTF-8 and ignoring the platform specs
import robot.utils.encoding
robot.utils.encoding.OUTPUT_ENCODING = 'UTF-8'
# RF 2.6.3 and RF 2.5.7
robot.utils.encoding._output_encoding = robot.utils.encoding.OUTPUT_ENCODING

try:
    # RF 2.7.5
    from robot.running import EXECUTION_CONTEXTS


    def _is_logged(level):
        current = EXECUTION_CONTEXTS.current
        if current is None:
            return True
        out = current.output
        if out is None:
            return True
        return out._xmllogger._log_message_is_logged(level)
except ImportError:
    # RF 2.5.6
    # RF 2.6.3
    def _is_logged(level):
        # Needs to be imported in the function as OUTPUT is not a constant
        from robot.output import OUTPUT

        if OUTPUT is None:
            return True
        return OUTPUT._xmllogger._log_message_is_logged(level)

        
def _fix_unicode(max_length, data):
    if sys.version_info < (3, 0, 0) and isinstance(data, unicode):
        return _truncate(max_length, data.encode('utf-8'))
    elif sys.version_info < (3, 0, 0) and isinstance(data, basestring):
        return _truncate(max_length, data.encode('unicode_escape'))
    elif sys.version_info >= (3, 0, 0) and isinstance(data, str):
        return _truncate(max_length, data)
    elif isinstance(data, dict):
        data = dict((_fix_unicode(max_length, k), _fix_unicode(max_length, data[k])) for k in data)
    elif isinstance(data, list):
        range_fun = xrange if sys.version_info < (3, 0, 0) else range
        for i in range_fun(0, len(data)):
            data[i] = _fix_unicode(max_length, data[i])
    elif data is None:
        data = _fix_unicode(max_length, 'None')
    else:
        data = _fix_unicode(max_length, str(data))
    return data
    
def _truncate(max_length, s):
    return s[:max_length] + ' <truncated>' if len(s) > max_length else s


class TestRunnerAgent:
    """Pass all listener events to a remote listener

    If called with one argument, that argument is a port
    If called with two, the first is a hostname, the second is a port
    """
    ROBOT_LISTENER_API_VERSION = 2
    
    CONNECTION_SLEEP_BETWEEN_TRIALS = 2
    
    RED_AGENT_PROTOCOL_VERSION = 1
    
    MAX_VARIABLE_VALUE_TEXT_LENGTH = 2048

    def __init__(self, *args):
        if len(args) == 1:
            self.host, self.port, connection_timeout = 'localhost', int(args[0]), 30
        elif len(args) == 2:
            self.host, self.port, connection_timeout = args[0], int(args[1]), 30
        else:
            self.host, self.port, connection_timeout = args[0], int(args[1]), int(args[2])
        
        self.sock = None
        self.decoder_encoder = None
        
        if self._connect(connection_timeout):
            self._is_debug_enabled, wait_for_signal = self._send_agent_initializing()
            self._send_version()
            if self._check_protocol_version():
                self._send_global_variables()
            else:
                self._is_debug_enabled, wait_for_signal = False, False
        else:
            self._is_debug_enabled, wait_for_signal = False, False
        
        self._debugger = RobotDebugger(self._is_debug_enabled)
        self._create_kill_server()
        
        self._wait_for_requestor(wait_for_signal)

    def _wait_for_requestor(self, wait_for_signal):
        if wait_for_signal:
            self._send_to_server('ready_to_start')
            self._wait_for_reponse('do_start')

    def _create_kill_server(self):
        self._killer = RobotKillerServer(self._debugger)
        self._server_thread = threading.Thread(
                target=self._killer.serve_forever)
        self._server_thread.setDaemon(True)
        self._server_thread.start()
        self._send_server_port(self._killer.server_address[1])

    def _send_agent_initializing(self):
        self._send_to_server('agent_initializing', '')
        return self._receive_operating_mode()
        
    def _receive_operating_mode(self):
        _, response = self._wait_for_reponse('operating_mode')
        operating_mode = response['operating_mode']
        return operating_mode['mode'].lower() == 'debug', operating_mode['wait_for_start_allowance']
        
    def _send_version(self):
        from robot import version
        robot_version = 'Robot Framework ' + version.get_full_version()
        info = {'python' : sys.version, 'robot' : robot_version, 'protocol' : self.RED_AGENT_PROTOCOL_VERSION}
        self._send_to_server('version', info)
        
    def _check_protocol_version(self):
        _, response = self._wait_for_reponse('protocol_version')
        is_correct = response['protocol_version']['is_correct']
        
        if not is_correct:
            self._close_connection()
            self._print_error_message('TestRunnerAgent <-> RED protocol version mismatch. ' +
                 'Closing connection. Please use agent script as exported from RED instance you\'re using')
        return is_correct
        
    def _send_global_variables(self):
        variables = {}
        try:
            try:
                from robot.variables import GLOBAL_VARIABLES
                variables = GLOBAL_VARIABLES
            except ImportError:  # for robot >2.9
                from robot.conf.settings import RobotSettings
                from robot.variables.scopes import GlobalVariables
                variables = GlobalVariables(RobotSettings()).as_dict()

            data = {}
            for key in variables.keys():
                new_key = '${' + key + '}' if not key.startswith('${') and not key.startswith('@{') else key
                data[new_key] = str(variables[key])
            self._send_to_server('global_vars', 'global_vars', data)
        except Exception as e:
            self._print_error_message(
                'Global variables sending error: ' + str(e) + ' Global variables: ' + str(variables))

    def _send_server_port(self, port):
        self._send_to_server('port', port)

    def start_test(self, name, attrs):
        self._send_to_server('start_test', name, attrs)

    def end_test(self, name, attrs):
        self._send_to_server('end_test', name, attrs)

    def start_suite(self, name, attrs):
        self._send_to_server('start_suite', name, attrs)

    def end_suite(self, name, attrs):
        self._send_to_server('end_suite', name, attrs)

    def start_keyword(self, name, attrs):
        # we're cutting args from original attrs dictionary, because it may contain 
        # objects which are not json-serializable and we don't need them anyway
        attrs_copy = copy.copy(attrs)
        attrs_copy['args'] = list()
        self._send_to_server('start_keyword', name, attrs_copy)
        
        if self._is_debug_enabled:
            self._send_vars()
            if self._should_stop_on_breakpoint():
                self._send_to_server('paused')
                self._wait_for_resume()

    def _send_vars(self):
        vars = {}
        try:
            from robot.libraries.BuiltIn import BuiltIn
            vars = BuiltIn().get_variables()
            data = {}
            for k in vars.keys():
                value = vars[k]
                if not inspect.ismodule(value) and not inspect.isfunction(value) and not inspect.isclass(value):
                    try:
                        if type(value) is list or isinstance(value, dict):
                            data[k] = _fix_unicode(self.MAX_VARIABLE_VALUE_TEXT_LENGTH, copy.copy(value))
                        else:
                            data[k] = str(_fix_unicode(self.MAX_VARIABLE_VALUE_TEXT_LENGTH, value))
                    except:
                        data[k] = 'None'
            self._send_to_server('vars', 'vars', data)
        except Exception as e:
            self._print_error_message('Variables sending error: ' + str(e) + ' Current variables: ' + str(vars))

    def _wait_for_resume(self):
        resumed = False
        while not resumed:
            response_name, response = self._wait_for_reponse('resume', 'interrupt', 'variable_change')
        
            if response_name == 'interrupt':
                sys.exit()
            elif response_name == 'variable_change':
                self._check_changed_variable(response)
            else:
                self._debugger.resume()
                resumed = True

    def _should_stop_on_breakpoint(self):
        self._send_to_server('check_condition')
        while True:
            response_name, response = self._wait_for_reponse('stop', 'continue', 'interrupt', 'keyword_condition')
            if response_name == 'stop':
                return True
            elif response_name == 'continue':
                return False
            elif response_name == 'interrupt':
                sys.exit()
            elif response_name == 'keyword_condition':
                self._run_condition_keyword(response)

    def _run_condition_keyword(self, condition):
        try:
            elements = condition['keyword_condition']
            keywordName, argList = elements[0], elements[1:]
            
            from robot.libraries.BuiltIn import BuiltIn
            result = BuiltIn().run_keyword_and_return_status(keywordName, *argList)
            
            self._send_to_server('condition_result', result)
        except Exception as e:
            self._send_to_server('condition_error', str(e))
        self._send_to_server('condition_checked')

    def _check_changed_variable(self, data):
        try:
            js = data['variable_change']
            from robot.libraries.BuiltIn import BuiltIn
            vars = BuiltIn().get_variables()
            for key in js.keys():
                if key in vars:
                    if len(js[key]) > 1:
                        from robot.libraries.Collections import Collections
                        if len(js[key]) == 2:
                            if isinstance(vars[key], dict):
                                Collections().set_to_dictionary(vars[key], js[key][0], js[key][1])
                            else:
                                Collections().set_list_value(vars[key], js[key][0], js[key][1])
                        else:
                            nestedList = vars[key]
                            newValue = ''
                            newValueIndex = 0
                            indexList = 1
                            for value in js[key]:
                                if indexList < (len(js[key]) - 1):
                                    nestedList = Collections().get_from_list(nestedList, int(value))
                                    indexList = indexList + 1
                                elif indexList == (len(js[key]) - 1):
                                    newValueIndex = int(value)
                                    indexList = indexList + 1
                                elif indexList == len(js[key]):
                                    newValue = value
                            Collections().set_list_value(nestedList, newValueIndex, newValue)
                    else:
                        BuiltIn().set_test_variable(key, js[key][0])
        except Exception as e:
            self._print_error_message('Setting variables error: ' + str(e) + ' Received data:' + str(data))

    def end_keyword(self, name, attrs):
        attrs_copy = copy.copy(attrs)
        attrs_copy['args'] = list()
        self._send_to_server('end_keyword', name, attrs_copy)
        self._debugger.end_keyword(attrs['status'] == 'PASS')

    def resource_import(self, name, attributes):
        self._send_to_server('resource_import', name, attributes)

    def library_import(self, name, attributes):
        # equals org.python.core.ClasspathPyImporter.PYCLASSPATH_PREFIX
        import platform
        if 'Jython' in platform.python_implementation():
            import org.python.core.imp as jimp
            if attributes['source']:
                if '__pyclasspath__' in attributes['source']:
                    res = attributes['source'].split('__pyclasspath__')[1].replace(os.sep, '')
                    attributes['source'] = str(jimp.getSyspathJavaLoader().getResources(res).nextElement())
            else:
                try:
                    source_uri = jimp.getSyspathJavaLoader().getResources(name + '.class').nextElement()
                    attributes['source'] = str(source_uri)
                except:
                    pass

            source_uri_txt = attributes['source']
            if source_uri_txt and 'file:/' in source_uri_txt:
                import re
                from java.io import File as File
                from java.net import URL as URL
                filePath = re.split('.*(?=file[:])', source_uri_txt)
                if len(filePath) > 1:
                    path = re.split('[!][/]', filePath[1])[0]
                    f = File(URL(path).getFile())
                    source_uri_txt = f.getAbsolutePath()
                attributes['source'] = source_uri_txt
        self._send_to_server('library_import', name, attributes)

    def message(self, message):
        if message['level'] in ('ERROR', 'FAIL', 'NONE'):
            self._send_to_server('message', message)

    def log_message(self, message):
        if _is_logged(message['level']):
            message['message'] = _truncate(self.MAX_VARIABLE_VALUE_TEXT_LENGTH, message['message'])
            self._send_to_server('log_message', message)

    def log_file(self, path):
        self._send_to_server('log_file', path)

    def output_file(self, path):
        self._send_to_server('output_file', path)

    def report_file(self, path):
        self._send_to_server('report_file', path)

    def summary_file(self, path):
        pass

    def debug_file(self, path):
        pass

    def close(self):
        self._send_to_server('close')
        self._close_connection()
            
    def _close_connection(self):
        if self.sock:
            self.decoder_encoder.close()
            self.decoder_encoder = None
            
            self.sock.close()           
            self.sock = None

    def _print_error_message(self, message):
        sys.stderr.write('[ ERROR ] ' + message + '\n')
        sys.stderr.flush()

    def _connect(self, connection_timeout):
        '''Establish a connection for sending data'''
        trials = 1
        start = time.time()
        
        while int(time.time() - start) < connection_timeout:
            try:
                self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.sock.connect((self.host, self.port))
                self.decoder_encoder = MessagesDecoderEncoder(self.sock)
                return True
            except socket.error as e:
                print('TestRunnerAgent: connection trial #%s failed' % trials)
                print('\tUnable to open socket to "%s:%s"'  % (self.host, self.port))
                print('\terror: %s' % str(e))
                self.sock = None
                self.decoder_encoder = None
                time.sleep(self.CONNECTION_SLEEP_BETWEEN_TRIALS)
            trials += 1
        return False

    def _send_to_server(self, name, *args):
        try:
            if self.decoder_encoder:
                packet = {name: args}
                self.decoder_encoder.dump(packet)
        except Exception:
            import traceback

            traceback.print_exc(file=sys.stdout)
            sys.stdout.flush()
            raise
        
    def _wait_for_reponse(self, *expected_responses):
        response = self._receive_from_server()
        response_key = list(response.keys())[0]
        while not response_key in expected_responses:
            response = self._receive_from_server()
            response_key = list(response.keys())[0]
        return response_key, response
    
    def _receive_from_server(self):
        try:
            if self.decoder_encoder:
                return self.decoder_encoder.load()
        except Exception:
            import traceback

            traceback.print_exc(file=sys.stdout)
            sys.stdout.flush()
            raise

class MessagesDecoderEncoder(object):
    
    def __init__(self, sock):
        self._json_encoder = json.JSONEncoder(separators=(',', ':'), sort_keys=True).encode
        self._json_decoder = json.JSONDecoder(strict=False).decode
        # IronPython does not return right object type if not binary mode
        self._file_to_write = sock.makefile('wb')
        self._file_to_read = sock.makefile('rb')

    def dump(self, obj):
        if not self._can_write():
            return
        json_string = self._json_encoder(obj) + '\n'
        if sys.version_info < (3, 0, 0):
            self._file_to_write.write(json_string)
        else:
            self._file_to_write.write(bytes(json_string, 'UTF-8'))
        self._file_to_write.flush()
            
    def load(self):
        if not self._can_read():
            return
        json_string = self._file_to_read.readline();
        if sys.version_info < (3, 0, 0):
            return self._json_decoder(json_string)
        else:
            return self._json_decoder(str(json_string, 'UTF-8'))
    
    def _can_write(self):
        return self._file_to_write is not None
    
    def _can_read(self):
        return self._file_to_read is not None
    
    def close(self):
        if self._can_write():
            self._file_to_write.close()
        if self._can_read():
            self._file_to_read.close()
        

class RobotDebugger(object):
    def __init__(self, pause_on_failure=False):
        self._state = 'running'
        self._keyword_level = 0
        self._pause_when_on_level = -1
        self._pause_on_failure = pause_on_failure
        self._resume = threading.Event()

    @staticmethod
    def is_breakpoint(name, attrs):
        return name == 'BuiltIn.Comment' and attrs['args'] == ['PAUSE']

    def pause(self):
        self._resume.clear()
        self._state = 'pause'

    def pause_on_failure(self, pause):
        self._pause_on_failure = pause

    def resume(self):
        self._state = 'running'
        self._pause_when_on_level = -1
        self._resume.set()

    def step_next(self):
        self._state = 'step_next'
        self._resume.set()

    def step_over(self):
        self._state = 'step_over'
        self._resume.set()

    def start_keyword(self):
        while self._state == 'pause':
            self._resume.wait()
            self._resume.clear()
        if self._state == 'step_next':
            self._state = 'pause'
        elif self._state == 'step_over':
            self._pause_when_on_level = self._keyword_level
            self._state = 'resume'
        self._keyword_level += 1

    def end_keyword(self, passed=True):
        self._keyword_level -= 1
        if self._keyword_level == self._pause_when_on_level \
                or (self._pause_on_failure and not passed):
            self._state = 'pause'

    def is_paused(self):
        return self._state == 'pause'
        

class RobotKillerServer(socketserver.TCPServer):
    allow_reuse_address = True

    def __init__(self, debugger):
        socketserver.TCPServer.__init__(self, ('', 0), RobotKillerHandler)
        self.debugger = debugger


class RobotKillerHandler(socketserver.StreamRequestHandler):
    def handle(self):
        data = self.request.makefile('r').read().strip()
        if data == 'kill':
            self._signal_kill()
        elif data == 'pause':
            self.server.debugger.pause()
        elif data == 'resume':
            self.server.debugger.resume()
        elif data == 'step_next':
            self.server.debugger.step_next()
        elif data == 'step_over':
            self.server.debugger.step_over()
        elif data == 'pause_on_failure':
            self.server.debugger.pause_on_failure(True)
        elif data == 'do_not_pause_on_failure':
            self.server.debugger.pause_on_failure(False)

    @staticmethod
    def _signal_kill():
        try:
            STOP_SIGNAL_MONITOR(1, '')
        except ExecutionFailed:
            pass