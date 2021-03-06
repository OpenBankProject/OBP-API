// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package code.obp.grpc.api

object ApiProto extends _root_.scalapb.GeneratedFileObject {
  lazy val dependencies: Seq[_root_.scalapb.GeneratedFileObject] = Seq(
    com.google.protobuf.empty.EmptyProto,
    com.google.protobuf.timestamp.TimestampProto
  )
  lazy val messagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_]] = Seq(
    code.obp.grpc.api.BanksJson400Grpc,
    code.obp.grpc.api.AccountsJSONGrpc,
    code.obp.grpc.api.AccountJSONGrpc,
    code.obp.grpc.api.ViewsJSONV121Grpc,
    code.obp.grpc.api.ViewJSONV121Grpc,
    code.obp.grpc.api.AccountsGrpc,
    code.obp.grpc.api.BasicAccountJSONGrpc,
    code.obp.grpc.api.BankIdGrpc,
    code.obp.grpc.api.BankIdUserIdGrpc,
    code.obp.grpc.api.AccountIdGrpc,
    code.obp.grpc.api.CoreTransactionsJsonV300Grpc,
    code.obp.grpc.api.BankIdAndAccountIdGrpc,
    code.obp.grpc.api.BankIdAccountIdAndUserIdGrpc,
    code.obp.grpc.api.AccountsBalancesV310JsonGrpc
  )
  private lazy val ProtoBytes: Array[Byte] =
      scalapb.Encoding.fromBase64(scala.collection.Seq(
  """CglhcGkucHJvdG8SDWNvZGUub2JwLmdycGMaG2dvb2dsZS9wcm90b2J1Zi9lbXB0eS5wcm90bxofZ29vZ2xlL3Byb3RvYnVmL
  3RpbWVzdGFtcC5wcm90byKSAwoQQmFua3NKc29uNDAwR3JwYxJFCgViYW5rcxgBIAMoCzIvLmNvZGUub2JwLmdycGMuQmFua3NKc
  29uNDAwR3JwYy5CYW5rSnNvbjQwMEdycGNSBWJhbmtzGksKF0JhbmtSb3V0aW5nSnNvblYxMjFHcnBjEhYKBnNjaGVtZRgBIAEoC
  VIGc2NoZW1lEhgKB2FkZHJlc3MYAiABKAlSB2FkZHJlc3Ma6QEKD0JhbmtKc29uNDAwR3JwYxIOCgJpZBgBIAEoCVICaWQSHQoKc
  2hvcnRfbmFtZRgCIAEoCVIJc2hvcnROYW1lEhsKCWZ1bGxfbmFtZRgDIAEoCVIIZnVsbE5hbWUSEgoEbG9nbxgEIAEoCVIEbG9nb
  xIYCgd3ZWJzaXRlGAUgASgJUgd3ZWJzaXRlElwKDWJhbmtfcm91dGluZ3MYBiADKAsyNy5jb2RlLm9icC5ncnBjLkJhbmtzSnNvb
  jQwMEdycGMuQmFua1JvdXRpbmdKc29uVjEyMUdycGNSDGJhbmtSb3V0aW5ncyJOChBBY2NvdW50c0pTT05HcnBjEjoKCGFjY291b
  nRzGAEgAygLMh4uY29kZS5vYnAuZ3JwYy5BY2NvdW50SlNPTkdycGNSCGFjY291bnRzIpsBCg9BY2NvdW50SlNPTkdycGMSDgoCa
  WQYASABKAlSAmlkEhQKBWxhYmVsGAIgASgJUgVsYWJlbBJJCg92aWV3c19hdmFpbGFibGUYAyADKAsyIC5jb2RlLm9icC5ncnBjL
  lZpZXdzSlNPTlYxMjFHcnBjUg52aWV3c0F2YWlsYWJsZRIXCgdiYW5rX2lkGAQgASgJUgZiYW5rSWQiSgoRVmlld3NKU09OVjEyM
  UdycGMSNQoFdmlld3MYASADKAsyHy5jb2RlLm9icC5ncnBjLlZpZXdKU09OVjEyMUdycGNSBXZpZXdzItkbChBWaWV3SlNPTlYxM
  jFHcnBjEg4KAmlkGAEgASgJUgJpZBIdCgpzaG9ydF9uYW1lGAIgASgJUglzaG9ydE5hbWUSIAoLZGVzY3JpcHRpb24YAyABKAlSC
  2Rlc2NyaXB0aW9uEhsKCWlzX3B1YmxpYxgEIAEoCFIIaXNQdWJsaWMSFAoFYWxpYXMYBSABKAlSBWFsaWFzEjwKG2hpZGVfbWV0Y
  WRhdGFfaWZfYWxpYXNfdXNlZBgGIAEoCFIXaGlkZU1ldGFkYXRhSWZBbGlhc1VzZWQSJgoPY2FuX2FkZF9jb21tZW50GAcgASgIU
  g1jYW5BZGRDb21tZW50EjsKGmNhbl9hZGRfY29ycG9yYXRlX2xvY2F0aW9uGAggASgIUhdjYW5BZGRDb3Jwb3JhdGVMb2NhdGlvb
  hIiCg1jYW5fYWRkX2ltYWdlGAkgASgIUgtjYW5BZGRJbWFnZRIpChFjYW5fYWRkX2ltYWdlX3VybBgKIAEoCFIOY2FuQWRkSW1hZ
  2VVcmwSKQoRY2FuX2FkZF9tb3JlX2luZm8YCyABKAhSDmNhbkFkZE1vcmVJbmZvEjwKG2Nhbl9hZGRfb3Blbl9jb3Jwb3JhdGVzX
  3VybBgMIAEoCFIXY2FuQWRkT3BlbkNvcnBvcmF0ZXNVcmwSOQoZY2FuX2FkZF9waHlzaWNhbF9sb2NhdGlvbhgNIAEoCFIWY2FuQ
  WRkUGh5c2ljYWxMb2NhdGlvbhIxChVjYW5fYWRkX3ByaXZhdGVfYWxpYXMYDiABKAhSEmNhbkFkZFByaXZhdGVBbGlhcxIvChRjY
  W5fYWRkX3B1YmxpY19hbGlhcxgPIAEoCFIRY2FuQWRkUHVibGljQWxpYXMSHgoLY2FuX2FkZF90YWcYECABKAhSCWNhbkFkZFRhZ
  xIeCgtjYW5fYWRkX3VybBgRIAEoCFIJY2FuQWRkVXJsEikKEWNhbl9hZGRfd2hlcmVfdGFnGBIgASgIUg5jYW5BZGRXaGVyZVRhZ
  xIsChJjYW5fZGVsZXRlX2NvbW1lbnQYEyABKAhSEGNhbkRlbGV0ZUNvbW1lbnQSQQodY2FuX2RlbGV0ZV9jb3Jwb3JhdGVfbG9jY
  XRpb24YFCABKAhSGmNhbkRlbGV0ZUNvcnBvcmF0ZUxvY2F0aW9uEigKEGNhbl9kZWxldGVfaW1hZ2UYFSABKAhSDmNhbkRlbGV0Z
  UltYWdlEj8KHGNhbl9kZWxldGVfcGh5c2ljYWxfbG9jYXRpb24YFiABKAhSGWNhbkRlbGV0ZVBoeXNpY2FsTG9jYXRpb24SJAoOY
  2FuX2RlbGV0ZV90YWcYFyABKAhSDGNhbkRlbGV0ZVRhZxIvChRjYW5fZGVsZXRlX3doZXJlX3RhZxgYIAEoCFIRY2FuRGVsZXRlV
  2hlcmVUYWcSMwoWY2FuX2VkaXRfb3duZXJfY29tbWVudBgZIAEoCFITY2FuRWRpdE93bmVyQ29tbWVudBI+ChxjYW5fc2VlX2Jhb
  mtfYWNjb3VudF9iYWxhbmNlGBogASgIUhhjYW5TZWVCYW5rQWNjb3VudEJhbGFuY2USQQoeY2FuX3NlZV9iYW5rX2FjY291bnRfY
  mFua19uYW1lGBsgASgIUhljYW5TZWVCYW5rQWNjb3VudEJhbmtOYW1lEkAKHWNhbl9zZWVfYmFua19hY2NvdW50X2N1cnJlbmN5G
  BwgASgIUhljYW5TZWVCYW5rQWNjb3VudEN1cnJlbmN5EjgKGWNhbl9zZWVfYmFua19hY2NvdW50X2liYW4YHSABKAhSFWNhblNlZ
  UJhbmtBY2NvdW50SWJhbhI6ChpjYW5fc2VlX2JhbmtfYWNjb3VudF9sYWJlbBgeIAEoCFIWY2FuU2VlQmFua0FjY291bnRMYWJlb
  BJVCihjYW5fc2VlX2JhbmtfYWNjb3VudF9uYXRpb25hbF9pZGVudGlmaWVyGB8gASgIUiNjYW5TZWVCYW5rQWNjb3VudE5hdGlvb
  mFsSWRlbnRpZmllchI8ChtjYW5fc2VlX2JhbmtfYWNjb3VudF9udW1iZXIYICABKAhSF2NhblNlZUJhbmtBY2NvdW50TnVtYmVyE
  jwKG2Nhbl9zZWVfYmFua19hY2NvdW50X293bmVycxghIAEoCFIXY2FuU2VlQmFua0FjY291bnRPd25lcnMSQQoeY2FuX3NlZV9iY
  W5rX2FjY291bnRfc3dpZnRfYmljGCIgASgIUhljYW5TZWVCYW5rQWNjb3VudFN3aWZ0QmljEjgKGWNhbl9zZWVfYmFua19hY2Nvd
  W50X3R5cGUYIyABKAhSFWNhblNlZUJhbmtBY2NvdW50VHlwZRIoChBjYW5fc2VlX2NvbW1lbnRzGCQgASgIUg5jYW5TZWVDb21tZ
  W50cxI7ChpjYW5fc2VlX2NvcnBvcmF0ZV9sb2NhdGlvbhglIAEoCFIXY2FuU2VlQ29ycG9yYXRlTG9jYXRpb24SKQoRY2FuX3NlZ
  V9pbWFnZV91cmwYJiABKAhSDmNhblNlZUltYWdlVXJsEiQKDmNhbl9zZWVfaW1hZ2VzGCcgASgIUgxjYW5TZWVJbWFnZXMSKQoRY
  2FuX3NlZV9tb3JlX2luZm8YKCABKAhSDmNhblNlZU1vcmVJbmZvEjwKG2Nhbl9zZWVfb3Blbl9jb3Jwb3JhdGVzX3VybBgpIAEoC
  FIXY2FuU2VlT3BlbkNvcnBvcmF0ZXNVcmwSQwofY2FuX3NlZV9vdGhlcl9hY2NvdW50X2JhbmtfbmFtZRgqIAEoCFIaY2FuU2VlT
  3RoZXJBY2NvdW50QmFua05hbWUSOgoaY2FuX3NlZV9vdGhlcl9hY2NvdW50X2liYW4YKyABKAhSFmNhblNlZU90aGVyQWNjb3Vud
  EliYW4SOgoaY2FuX3NlZV9vdGhlcl9hY2NvdW50X2tpbmQYLCABKAhSFmNhblNlZU90aGVyQWNjb3VudEtpbmQSQgoeY2FuX3NlZ
  V9vdGhlcl9hY2NvdW50X21ldGFkYXRhGC0gASgIUhpjYW5TZWVPdGhlckFjY291bnRNZXRhZGF0YRJXCiljYW5fc2VlX290aGVyX
  2FjY291bnRfbmF0aW9uYWxfaWRlbnRpZmllchguIAEoCFIkY2FuU2VlT3RoZXJBY2NvdW50TmF0aW9uYWxJZGVudGlmaWVyEj4KH
  GNhbl9zZWVfb3RoZXJfYWNjb3VudF9udW1iZXIYLyABKAhSGGNhblNlZU90aGVyQWNjb3VudE51bWJlchJDCh9jYW5fc2VlX290a
  GVyX2FjY291bnRfc3dpZnRfYmljGDAgASgIUhpjYW5TZWVPdGhlckFjY291bnRTd2lmdEJpYxIxChVjYW5fc2VlX293bmVyX2Nvb
  W1lbnQYMSABKAhSEmNhblNlZU93bmVyQ29tbWVudBI5ChljYW5fc2VlX3BoeXNpY2FsX2xvY2F0aW9uGDIgASgIUhZjYW5TZWVQa
  HlzaWNhbExvY2F0aW9uEjEKFWNhbl9zZWVfcHJpdmF0ZV9hbGlhcxgzIAEoCFISY2FuU2VlUHJpdmF0ZUFsaWFzEi8KFGNhbl9zZ
  WVfcHVibGljX2FsaWFzGDQgASgIUhFjYW5TZWVQdWJsaWNBbGlhcxIgCgxjYW5fc2VlX3RhZ3MYNSABKAhSCmNhblNlZVRhZ3MSO
  woaY2FuX3NlZV90cmFuc2FjdGlvbl9hbW91bnQYNiABKAhSF2NhblNlZVRyYW5zYWN0aW9uQW1vdW50Ej0KG2Nhbl9zZWVfdHJhb
  nNhY3Rpb25fYmFsYW5jZRg3IAEoCFIYY2FuU2VlVHJhbnNhY3Rpb25CYWxhbmNlEj8KHGNhbl9zZWVfdHJhbnNhY3Rpb25fY3Vyc
  mVuY3kYOCABKAhSGWNhblNlZVRyYW5zYWN0aW9uQ3VycmVuY3kSRQofY2FuX3NlZV90cmFuc2FjdGlvbl9kZXNjcmlwdGlvbhg5I
  AEoCFIcY2FuU2VlVHJhbnNhY3Rpb25EZXNjcmlwdGlvbhJECh9jYW5fc2VlX3RyYW5zYWN0aW9uX2ZpbmlzaF9kYXRlGDogASgIU
  htjYW5TZWVUcmFuc2FjdGlvbkZpbmlzaERhdGUSPwocY2FuX3NlZV90cmFuc2FjdGlvbl9tZXRhZGF0YRg7IAEoCFIZY2FuU2VlV
  HJhbnNhY3Rpb25NZXRhZGF0YRJRCiZjYW5fc2VlX3RyYW5zYWN0aW9uX290aGVyX2JhbmtfYWNjb3VudBg8IAEoCFIhY2FuU2VlV
  HJhbnNhY3Rpb25PdGhlckJhbmtBY2NvdW50EkIKHmNhbl9zZWVfdHJhbnNhY3Rpb25fc3RhcnRfZGF0ZRg9IAEoCFIaY2FuU2VlV
  HJhbnNhY3Rpb25TdGFydERhdGUSTwolY2FuX3NlZV90cmFuc2FjdGlvbl90aGlzX2JhbmtfYWNjb3VudBg+IAEoCFIgY2FuU2VlV
  HJhbnNhY3Rpb25UaGlzQmFua0FjY291bnQSNwoYY2FuX3NlZV90cmFuc2FjdGlvbl90eXBlGD8gASgIUhVjYW5TZWVUcmFuc2Fjd
  GlvblR5cGUSHgoLY2FuX3NlZV91cmwYQCABKAhSCWNhblNlZVVybBIpChFjYW5fc2VlX3doZXJlX3RhZxhBIAEoCFIOY2FuU2VlV
  2hlcmVUYWciTwoMQWNjb3VudHNHcnBjEj8KCGFjY291bnRzGAEgAygLMiMuY29kZS5vYnAuZ3JwYy5CYXNpY0FjY291bnRKU09OR
  3JwY1IIYWNjb3VudHMijgIKFEJhc2ljQWNjb3VudEpTT05HcnBjEg4KAmlkGAEgASgJUgJpZBIUCgVsYWJlbBgCIAEoCVIFbGFiZ
  WwSFwoHYmFua19pZBgDIAEoCVIGYmFua0lkEloKD3ZpZXdzX2F2YWlsYWJsZRgEIAMoCzIxLmNvZGUub2JwLmdycGMuQmFzaWNBY
  2NvdW50SlNPTkdycGMuQmFzaWNWaWV3SnNvblIOdmlld3NBdmFpbGFibGUaWwoNQmFzaWNWaWV3SnNvbhIOCgJpZBgBIAEoCVICa
  WQSHQoKc2hvcnRfbmFtZRgCIAEoCVIJc2hvcnROYW1lEhsKCWlzX3B1YmxpYxgDIAEoCFIIaXNQdWJsaWMiOgoKQmFua0lkR3JwY
  xIUCgV2YWx1ZRgBIAEoCVIFdmFsdWUSFgoGdXNlcklkGAIgASgJUgZ1c2VySWQiQgoQQmFua0lkVXNlcklkR3JwYxIWCgZiYW5rS
  WQYASABKAlSBmJhbmtJZBIWCgZ1c2VySWQYAiABKAlSBnVzZXJJZCIlCg1BY2NvdW50SWRHcnBjEhQKBXZhbHVlGAEgASgJUgV2Y
  Wx1ZSLNDgocQ29yZVRyYW5zYWN0aW9uc0pzb25WMzAwR3JwYxJrCgx0cmFuc2FjdGlvbnMYASADKAsyRy5jb2RlLm9icC5ncnBjL
  kNvcmVUcmFuc2FjdGlvbnNKc29uVjMwMEdycGMuQ29yZVRyYW5zYWN0aW9uSnNvblYzMDBHcnBjUgx0cmFuc2FjdGlvbnMa6gIKG
  0NvcmVUcmFuc2FjdGlvbkpzb25WMzAwR3JwYxIOCgJpZBgBIAEoCVICaWQSZgoMdGhpc19hY2NvdW50GAIgASgLMkMuY29kZS5vY
  nAuZ3JwYy5Db3JlVHJhbnNhY3Rpb25zSnNvblYzMDBHcnBjLlRoaXNBY2NvdW50SnNvblYzMDBHcnBjUgt0aGlzQWNjb3VudBJtC
  g1vdGhlcl9hY2NvdW50GAMgASgLMkguY29kZS5vYnAuZ3JwYy5Db3JlVHJhbnNhY3Rpb25zSnNvblYzMDBHcnBjLkNvcmVDb3Vud
  GVycGFydHlKc29uVjMwMEdycGNSDG90aGVyQWNjb3VudBJkCgdkZXRhaWxzGAQgASgLMkouY29kZS5vYnAuZ3JwYy5Db3JlVHJhb
  nNhY3Rpb25zSnNvblYzMDBHcnBjLkNvcmVUcmFuc2FjdGlvbkRldGFpbHNKU09OR3JwY1IHZGV0YWlscxpGChVBY2NvdW50SG9sZ
  GVySlNPTkdycGMSEgoEbmFtZRgBIAEoCVIEbmFtZRIZCghpc19hbGlhcxgCIAEoCFIHaXNBbGlhcxpOChpBY2NvdW50Um91dGluZ
  0pzb25WMTIxR3JwYxIWCgZzY2hlbWUYASABKAlSBnNjaGVtZRIYCgdhZGRyZXNzGAIgASgJUgdhZGRyZXNzGksKF0JhbmtSb3V0a
  W5nSnNvblYxMjFHcnBjEhYKBnNjaGVtZRgBIAEoCVIGc2NoZW1lEhgKB2FkZHJlc3MYAiABKAlSB2FkZHJlc3Ma4QIKF1RoaXNBY
  2NvdW50SnNvblYzMDBHcnBjEg4KAmlkGAEgASgJUgJpZBJmCgxiYW5rX3JvdXRpbmcYAiABKAsyQy5jb2RlLm9icC5ncnBjLkNvc
  mVUcmFuc2FjdGlvbnNKc29uVjMwMEdycGMuQmFua1JvdXRpbmdKc29uVjEyMUdycGNSC2JhbmtSb3V0aW5nEnEKEGFjY291bnRfc
  m91dGluZ3MYAyADKAsyRi5jb2RlLm9icC5ncnBjLkNvcmVUcmFuc2FjdGlvbnNKc29uVjMwMEdycGMuQWNjb3VudFJvdXRpbmdKc
  29uVjEyMUdycGNSD2FjY291bnRSb3V0aW5ncxJbCgdob2xkZXJzGAQgAygLMkEuY29kZS5vYnAuZ3JwYy5Db3JlVHJhbnNhY3Rpb
  25zSnNvblYzMDBHcnBjLkFjY291bnRIb2xkZXJKU09OR3JwY1IHaG9sZGVycxrkAgocQ29yZUNvdW50ZXJwYXJ0eUpzb25WMzAwR
  3JwYxIOCgJpZBgBIAEoCVICaWQSWQoGaG9sZGVyGAIgASgLMkEuY29kZS5vYnAuZ3JwYy5Db3JlVHJhbnNhY3Rpb25zSnNvblYzM
  DBHcnBjLkFjY291bnRIb2xkZXJKU09OR3JwY1IGaG9sZGVyEmYKDGJhbmtfcm91dGluZxgDIAEoCzJDLmNvZGUub2JwLmdycGMuQ
  29yZVRyYW5zYWN0aW9uc0pzb25WMzAwR3JwYy5CYW5rUm91dGluZ0pzb25WMTIxR3JwY1ILYmFua1JvdXRpbmcScQoQYWNjb3Vud
  F9yb3V0aW5ncxgEIAMoCzJGLmNvZGUub2JwLmdycGMuQ29yZVRyYW5zYWN0aW9uc0pzb25WMzAwR3JwYy5BY2NvdW50Um91dGluZ
  0pzb25WMTIxR3JwY1IPYWNjb3VudFJvdXRpbmdzGk8KGUFtb3VudE9mTW9uZXlKc29uVjEyMUdycGMSGgoIY3VycmVuY3kYASABK
  AlSCGN1cnJlbmN5EhYKBmFtb3VudBgCIAEoCVIGYW1vdW50GtECCh5Db3JlVHJhbnNhY3Rpb25EZXRhaWxzSlNPTkdycGMSEgoEd
  HlwZRgBIAEoCVIEdHlwZRIgCgtkZXNjcmlwdGlvbhgCIAEoCVILZGVzY3JpcHRpb24SFgoGcG9zdGVkGAMgASgJUgZwb3N0ZWQSH
  AoJY29tcGxldGVkGAQgASgJUgljb21wbGV0ZWQSZgoLbmV3X2JhbGFuY2UYBSABKAsyRS5jb2RlLm9icC5ncnBjLkNvcmVUcmFuc
  2FjdGlvbnNKc29uVjMwMEdycGMuQW1vdW50T2ZNb25leUpzb25WMTIxR3JwY1IKbmV3QmFsYW5jZRJbCgV2YWx1ZRgGIAEoCzJFL
  mNvZGUub2JwLmdycGMuQ29yZVRyYW5zYWN0aW9uc0pzb25WMzAwR3JwYy5BbW91bnRPZk1vbmV5SnNvblYxMjFHcnBjUgV2YWx1Z
  SJOChZCYW5rSWRBbmRBY2NvdW50SWRHcnBjEhYKBmJhbmtJZBgBIAEoCVIGYmFua0lkEhwKCWFjY291bnRJZBgCIAEoCVIJYWNjb
  3VudElkImwKHEJhbmtJZEFjY291bnRJZEFuZFVzZXJJZEdycGMSFgoGYmFua0lkGAEgASgJUgZiYW5rSWQSHAoJYWNjb3VudElkG
  AIgASgJUglhY2NvdW50SWQSFgoGdXNlcklkGAMgASgJUgZ1c2VySWQixwUKHEFjY291bnRzQmFsYW5jZXNWMzEwSnNvbkdycGMSX
  goIYWNjb3VudHMYASADKAsyQi5jb2RlLm9icC5ncnBjLkFjY291bnRzQmFsYW5jZXNWMzEwSnNvbkdycGMuQWNjb3VudEJhbGFuY
  2VWMzEwR3JwY1IIYWNjb3VudHMSZgoPb3ZlcmFsbF9iYWxhbmNlGAIgASgLMj0uY29kZS5vYnAuZ3JwYy5BY2NvdW50c0JhbGFuY
  2VzVjMxMEpzb25HcnBjLkFtb3VudE9mTW9uZXlHcnBjUg5vdmVyYWxsQmFsYW5jZRIwChRvdmVyYWxsX2JhbGFuY2VfZGF0ZRgDI
  AEoCVISb3ZlcmFsbEJhbGFuY2VEYXRlGkcKEUFtb3VudE9mTW9uZXlHcnBjEhoKCGN1cnJlbmN5GAEgASgJUghjdXJyZW5jeRIWC
  gZhbW91bnQYAiABKAlSBmFtb3VudBpGChJBY2NvdW50Um91dGluZ0dycGMSFgoGc2NoZW1lGAEgASgJUgZzY2hlbWUSGAoHYWRkc
  mVzcxgCIAEoCVIHYWRkcmVzcxqbAgoWQWNjb3VudEJhbGFuY2VWMzEwR3JwYxIOCgJpZBgBIAEoCVICaWQSFAoFbGFiZWwYAiABK
  AlSBWxhYmVsEhcKB2JhbmtfaWQYAyABKAlSBmJhbmtJZBJpChBhY2NvdW50X3JvdXRpbmdzGAQgAygLMj4uY29kZS5vYnAuZ3JwY
  y5BY2NvdW50c0JhbGFuY2VzVjMxMEpzb25HcnBjLkFjY291bnRSb3V0aW5nR3JwY1IPYWNjb3VudFJvdXRpbmdzElcKB2JhbGFuY
  2UYBSABKAsyPS5jb2RlLm9icC5ncnBjLkFjY291bnRzQmFsYW5jZXNWMzEwSnNvbkdycGMuQW1vdW50T2ZNb25leUdycGNSB2Jhb
  GFuY2UymAMKCk9icFNlcnZpY2USRQoIZ2V0QmFua3MSFi5nb29nbGUucHJvdG9idWYuRW1wdHkaHy5jb2RlLm9icC5ncnBjLkJhb
  mtzSnNvbjQwMEdycGMiABJdChtnZXRQcml2YXRlQWNjb3VudHNBdE9uZUJhbmsSHy5jb2RlLm9icC5ncnBjLkJhbmtJZFVzZXJJZ
  EdycGMaGy5jb2RlLm9icC5ncnBjLkFjY291bnRzR3JwYyIAEmMKF2dldEJhbmtBY2NvdW50c0JhbGFuY2VzEhkuY29kZS5vYnAuZ
  3JwYy5CYW5rSWRHcnBjGisuY29kZS5vYnAuZ3JwYy5BY2NvdW50c0JhbGFuY2VzVjMxMEpzb25HcnBjIgASfwohZ2V0Q29yZVRyY
  W5zYWN0aW9uc0ZvckJhbmtBY2NvdW50EisuY29kZS5vYnAuZ3JwYy5CYW5rSWRBY2NvdW50SWRBbmRVc2VySWRHcnBjGisuY29kZ
  S5vYnAuZ3JwYy5Db3JlVHJhbnNhY3Rpb25zSnNvblYzMDBHcnBjIgBiBnByb3RvMw=="""
      ).mkString)
  lazy val scalaDescriptor: _root_.scalapb.descriptors.FileDescriptor = {
    val scalaProto = com.google.protobuf.descriptor.FileDescriptorProto.parseFrom(ProtoBytes)
    _root_.scalapb.descriptors.FileDescriptor.buildFrom(scalaProto, dependencies.map(_.scalaDescriptor))
  }
  lazy val javaDescriptor: com.google.protobuf.Descriptors.FileDescriptor = {
    val javaProto = com.google.protobuf.DescriptorProtos.FileDescriptorProto.parseFrom(ProtoBytes)
    com.google.protobuf.Descriptors.FileDescriptor.buildFrom(javaProto, Array(
      com.google.protobuf.empty.EmptyProto.javaDescriptor,
      com.google.protobuf.timestamp.TimestampProto.javaDescriptor
    ))
  }
  @deprecated("Use javaDescriptor instead. In a future version this will refer to scalaDescriptor.", "ScalaPB 0.5.47")
  def descriptor: com.google.protobuf.Descriptors.FileDescriptor = javaDescriptor
}