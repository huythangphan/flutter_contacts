import 'package:flutter_test/flutter_test.dart';
import 'package:contacts_service/contacts_service.dart';
import 'package:contacts_service/contacts_service_platform_interface.dart';
import 'package:contacts_service/contacts_service_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockContactsServicePlatform
    with MockPlatformInterfaceMixin
    implements ContactsServicePlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final ContactsServicePlatform initialPlatform = ContactsServicePlatform.instance;

  test('$MethodChannelContactsService is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelContactsService>());
  });

  test('getPlatformVersion', () async {
    ContactsService contactsServicePlugin = ContactsService();
    MockContactsServicePlatform fakePlatform = MockContactsServicePlatform();
    ContactsServicePlatform.instance = fakePlatform;

    expect(await contactsServicePlugin.getPlatformVersion(), '42');
  });
}
