# Copyright 2022 Evan Saulpaugh
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
from abiv3 import Utils, RLPItem


class RLPIterator:

    next_item = None

    def __init__(self, buffer, index, container_end):
        self.buffer = buffer
        self.index = index
        self.container_end = container_end

    @staticmethod
    def sequence_iterator(buffer, index):
        return RLPIterator(buffer, index, len(buffer))

    def has_next(self) -> bool:
        if self.next_item is not None:
            return True
        if self.index < self.container_end:
            self.next_item = Utils.wrap(self.buffer, self.index, self.container_end)
            self.index = self.next_item.endIndex
            return True
        return False

    def next(self) -> RLPItem:
        if self.has_next():
            it = self.next_item
            self.next_item = None
            self.index = it.endIndex
            return it
        raise Exception('no such element')
