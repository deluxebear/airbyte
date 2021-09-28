#
# Copyright (c) 2021 Airbyte, Inc., all rights reserved.
#

from abc import ABC, abstractmethod
from typing import Any, List, Mapping

import pytest
from airbyte_cdk import AirbyteLogger
from smart_open import open as smart_open


class AbstractTestParser(ABC):
    """ Prefix this class with Abstract so the tests don't run here but only in the children """

    logger = AirbyteLogger()

    @property
    @abstractmethod
    def test_files(self) -> List[Mapping[str, Any]]:
        """return a list of test_file dicts in structure:
        [
            {"AbstractFileParser": CsvParser(format, master_schema), "filepath": "...", "num_records": 5, "inferred_schema": {...}, line_checks:{}, fails: []},
            {"AbstractFileParser": CsvParser(format, master_schema), "filepath": "...", "num_records": 16, "inferred_schema": {...}, line_checks:{}, fails: []}
        ]
        note: line_checks index is 1-based to align with row numbers
        """

    def _get_readmode(self, test_name, test_file):
        self.logger.info(f"testing {test_name}() with {test_file.get('test_alias', test_file['filepath'].split('/')[-1])} ...")
        return "rb" if test_file["AbstractFileParser"].is_binary else "r"

    def test_get_inferred_schema(self):
        for test_file in self.test_files:
            with smart_open(test_file["filepath"], self._get_readmode("get_inferred_schema", test_file)) as f:
                if "test_get_inferred_schema" in test_file["fails"]:
                    with pytest.raises(Exception) as e_info:
                        test_file["AbstractFileParser"].get_inferred_schema(f)
                        self.logger.debug(str(e_info))
                else:
                    assert test_file["AbstractFileParser"].get_inferred_schema(f) == test_file["inferred_schema"]

    def test_stream_records(self):
        for test_file in self.test_files:
            with smart_open(test_file["filepath"], self._get_readmode("stream_records", test_file)) as f:
                if "test_stream_records" in test_file["fails"]:
                    with pytest.raises(Exception) as e_info:
                        [print(r) for r in test_file["AbstractFileParser"].stream_records(f)]
                        self.logger.debug(str(e_info))
                else:
                    records = [r for r in test_file["AbstractFileParser"].stream_records(f)]

                    assert len(records) == test_file["num_records"]
                    for index, expected_record in test_file["line_checks"].items():
                        assert records[index - 1] == expected_record
