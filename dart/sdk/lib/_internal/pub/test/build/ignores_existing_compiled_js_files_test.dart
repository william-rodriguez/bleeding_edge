// Copyright (c) 2013, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import 'package:scheduled_test/scheduled_test.dart';

import '../descriptor.dart' as d;
import '../test_pub.dart';

main() {
  initConfig();

  integration("ignores existing JS files that were compiled to Dart", () {
    // Dart2js can take a long time to compile dart code, so we increase the
    // timeout to cope with that.
    currentSchedule.timeout *= 3;

    d.dir(appPath, [
      d.appPubspec(),
      d.dir('web', [
        d.file('file.dart', 'void main() => print("hello");'),
        d.file('file.dart.js', 'some js code'),
        d.dir('subdir', [
          d.file('subfile.dart', 'void main() => print("ping");'),
          d.file('subfile.dart.js', 'some js code')
        ])
      ])
    ]).create();

    schedulePub(args: ["build"],
        output: new RegExp(r"Built 6 files!"),
        exitCode: 0);

    d.dir(appPath, [
      d.dir('build', [
        d.matcherFile('file.dart.js', isNot(equals('some js code'))),
        d.dir('subdir', [
          d.matcherFile('subfile.dart.js', isNot(equals('some js code')))
        ])
      ])
    ]).validate();
  });
}
