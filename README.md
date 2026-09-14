# Live Sân Đấu — 1.22.0 • Điều khiển Pickleball gọn và bảng điểm truyền hình

**Trạng thái: mã nguồn 1.22 đã đưa chọn tay giao bóng vào đúng cụm tăng/giảm điểm và thiết kế lại bảng Pickleball.**

> Bản GitHub Actions dùng khóa debug tạm thời của máy build và không công khai khóa ký cũ. Khi cài APK từ Actions, có thể phải gỡ bản thử nghiệm cũ nếu Android báo chữ ký không khớp.

- Mỗi đội có hai nút **GIAO T1 / GIAO T2** ngay bên dưới nút cộng/trừ điểm của chính đội đó; không phải mở cài đặt và không có hàng chọn riêng che camera.
- Nút đang chọn sáng xanh; chạm lại đúng nút đang sáng để bỏ trạng thái giao bóng.
- Khi đội A hoặc B giao bóng, hàng của đội đó trên bảng truyền hình hiện ký hiệu xanh dọc.
- Tay giao số 1 hiện **một vạch xanh**; tay giao số 2 hiện **hai vạch xanh**.
- Bảng Pickleball mới có dải tên giải màu nổi, hai hàng đội/VĐV, cột set và điểm tách rõ, ô điểm lớn, trạng thái giao bóng và đồng hồ ở chân bảng.
- Bố cục gọn ở góc trên trái theo phong cách truyền hình, ưu tiên khả năng đọc trên màn hình điện thoại; không dùng logo của đài hay giải đấu khác.
- Cả bốn phong cách màu Pickleball dùng chung lưới bố cục mới; bóng đá và bóng chuyền giữ bố cục riêng.
- Thêm kiểm thử vị trí điều khiển trong từng đội, lưới bảng chuyên nghiệp, số vạch và vị trí theo đội A/B.
- Tăng `versionCode` lên 23 và `versionName` lên `1.22.0`.

---

# Lịch sử — 1.19.0 • Bảng tỉ số truyền hình gọn cho điện thoại

**Trạng thái: mã nguồn đã cập nhật; chưa có APK 1.19 được biên dịch trong lượt này.**

- Mẫu mặc định `ARENA` đổi tên hiển thị thành **Truyền hình • xanh đêm**.
- Bóng đá dùng nền xanh đen, hai ô điểm xanh ngọc riêng, tên đội hai bên và không còn viền đỏ dày.
- Hàng phụ gom nhãn LIVE, đồng hồ, hiệp và tên giải; thông tin phụ nhỏ hơn để ưu tiên tỉ số.
- Tăng cỡ mặc định của bảng bóng đá từ `0.60` lên `0.68` để dễ đọc trên màn hình điện thoại nhưng vẫn dưới 40% chiều ngang video 1280 × 720.
- Thanh chữ chạy cao 48 px, nền tối, nhãn trực tiếp màu đỏ nhỏ; chữ giảm từ 27 px xuống 23 px để tránh che hình và bị cắt như bản thử trên điện thoại.
- Giữ nguyên cách kéo/chụm đổi vị trí và kích thước, dữ liệu trận, camera, bộ lọc, replay, FPS và cấu hình livestream của 1.18.
- Tăng `versionCode` lên 20 và `versionName` lên `1.19.0`.

Ảnh SVG xem trước được dựng từ đúng màu sắc, kích thước và tọa độ của mẫu mới. Môi trường hiện tại không có Android SDK/Gradle nên chưa chạy Android compile, lint hoặc tạo APK; cần build và thử trên điện thoại trước khi dùng cho trận chính thức.

---

# Lịch sử — 1.18.0 • Sửa khởi tạo FPS và thử nhiều cấu hình

**Trạng thái: mã nguồn đã sửa; chưa có APK 1.18 được biên dịch trong lượt này.**

- Android 9 / API 28 trở lên: cả phiên AE tự động và manual đều truyền request ban đầu qua `SessionConfiguration.setSessionParameters` trước khi tạo capture session. Trước đây bước này chỉ có trong nhánh manual. Thiết bị chỉ áp dụng các session key được công bố; không bảo đảm FPS chỉ nhờ đặt tham số.
- Thử tối đa 8 tổ hợp camera/kích thước/dải AE hợp lệ cho đúng FPS và độ phân giải đã chọn. Ưu tiên chế độ AE thông thường, rồi mới manual; mỗi camera có lượt thử cấu hình tốt nhất trước khi thử thêm cấu hình cùng ID. Loại bỏ tổ hợp trùng.
- Chờ camera đóng xong trước khi thử lại, bao gồm trường hợp khởi tạo preview lỗi giữa chừng. Callback cũ bị vô hiệu bằng generation. Không tự thử lại camera khi đang phát trực tiếp hoặc ứng dụng đã ra nền.
- Sau khi hết lựa chọn mới khôi phục cấu hình cũ. Khôi phục preview 30 FPS không được coi là áp dụng thành công 60 FPS và không tự bắt đầu phát luồng.
- Giới hạn FPS đồ họa được đặt sau `prepareVideo`. Tắt chống rung điện tử của camera khi thử FPS >30 nếu khóa OFF được công bố; không thay bộ lọc, scoreboard, replay, bitrate hay âm thanh.
- Vẫn đo riêng cảm biến, khung hình mới nhận qua GL và FPS mã hóa. Lỗi phân biệt cảm biến chưa đạt với khâu nhận/xử lý hình không theo kịp. Báo cáo giữ các lần thử; giao diện ghi rõ lượt thử hiện tại.
- Không bỏ kiểm tra khung hình thực. Giữ ngưỡng >=95% mục tiêu trong hai cửa sổ liên tiếp; bỏ qua cửa sổ GL đầu tiên nếu chưa có số đo cảm biến. Không nhân đôi khung hình, không lấy nguồn 120/240 FPS thay cho 60.
- Giữ package `com.vangnang.livecamera`, khóa ký thử nghiệm và các chức năng của 1.17; tăng versionCode lên 19 / versionName 1.18.0. Chưa tích hợp Google/Facebook OAuth trong bản sửa FPS này.

## Kiểm tra bản sửa 1.18

- 67 kiểm thử JVM chuyên về cấu hình, chọn camera, hàng đợi thử lại, thời gian phơi sáng, kiểm tra FPS và URL đã đạt. Chạy bằng Kotlin 2.0.21 / JUnit 4.13.2 có sẵn trong Gradle 8.13. Đây là nhóm kiểm thử độc lập, không phải toàn bộ bộ test Android.
- Lệnh Gradle ngoại tuyến `:app:testDebugUnitTest :app:assembleDebug` thất bại ngay lúc tìm plugin `com.android.application:8.13.2`; plugin chưa có trong cache. Chưa chạy được Android compile/lint hoặc xác minh chữ ký APK mới.
- Không thử trên A06/LG V40 hay camera thật. Những thay đổi này xử lý điểm chưa hợp lý trong code; chưa chứng minh sẽ đạt 60 FPS trên thiết bị cụ thể.
- Có thể chạy lại nhóm test ngoại tuyến bằng `bash tools/test-fps-offline.sh /duong-dan/gradle-8.13/lib /duong-dan/java17/bin/java`.

## Khi đã biên dịch được APK

Thử camera sau ở nơi đủ sáng, chọn 1080p / 60 FPS và ÁP DỤNG. Quan sát lượt thử (tối đa 8; có thể mất khoảng 1–2 phút nếu mọi cấu hình đều thất bại). Nếu sẵn sàng, phát riêng tư và kiểm tra cả cảm biến / GL / mã hóa, hình chuyển động và âm thanh. Nếu lỗi, sao chép **Chẩn đoán camera / FPS**, gồm tất cả các lần thử; không nhầm FPS của preview 30 đã khôi phục với kết quả yêu cầu 60.

Tài liệu Android đối chiếu: https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AE_TARGET_FPS_RANGE

---

# Lịch sử — 1.17.0 • Thử FPS trung gian

- Bộ chọn FPS có 30, 40, 45, 50, 60. Một danh sách chung cấp giá trị cho giao diện, kiểm tra cấu hình và chẩn đoán; không ánh xạ mọi lựa chọn khác30 thành60.
- Với dải AE phù hợp được camera công bố, ưu tiên AE. Nếu không có, thử manual cho đúng mức đã chọn khi camera có đủ khóa điều khiển, khoảng ISO/phơi sáng hợp lệ và min frame duration phù hợp. Không chọn nguồn60/120 thay cho40.
- Manual40 yêu cầu frame duration25.000.000 ns; 45 yêu cầu22.222.223 ns; 50 yêu cầu20.000.000 ns; 60 giữ16.666.667 ns. Phơi sáng tối đa nửa thời gian khung và bù ISO trong giới hạn máy. Phơi sáng/ISO khóa sau đo sáng; đổi ánh sáng cần dừng live rồi ÁP DỤNG lại.
- Cả cảm biến và hình vào bộ lọc phải đạt95% mục tiêu trong hai cửa sổ liên tiếp để sẵn sàng: 40 cần38 FPS, 45 cần42,75 FPS, 50 cần47,5 FPS, 60 cần57 FPS. Nguồn30 không được xác nhận thành40. Khi đang live, FPS thấp chỉ cảnh báo như bản trước.
- Camera, giới hạn FPS của bộ lọc và bộ mã hóa đều nhận đúng FPS đã chọn. Mức40/45/50 dùng bitrate của cấu hình60 cùng độ phân giải; preset30/60 và tính năng khác giữ nguyên.
- Vẫn camera → bộ lọc/bảng điểm → encoder. Không có nguồn120, mã hóa/giải mã trung gian hoặc nhân đôi khung hình. Nhãn manual ghi rõ đây là mục tiêu cần xác nhận bằng số đo; metadata không phải bảo đảm FPS.

**Chưa thử các mức mới trên A06/LG thật.** Việc chọn40 có thể vẫn trả30 hoặc camera/codec từ chối. Tương thích phát đầu cuối ở các mức trung gian chưa được xác nhận. Thử preview trước, rồi live riêng tư trước khi dùng cho trận chính thức.

Cách thử: cài cập nhật1.17, chọn camera sau1080p40 → ÁP DỤNG, chờ khoảng3–15 giây. Nếu ổn, thử45 rồi50. Nếu lỗi, thử720p40 và gửi phần “Lần lỗi gần nhất”; không nhầm số đo cấu hình30 đã khôi phục với lần thử40.

VersionCode18/versionName1.17.0; giữ cùng package và khóa ký bản1.16.

Kiểm tra bản dựng: assembleDebug thành công; 187 unit tests, 0 thất bại; lintDebug 0 lỗi/107 cảnh báo. Các test mới kiểm tra thời gian khung40/45/50, giới hạn cảm biến, lựa chọn đúng mứcFPS và việc nguồn30 không qua ngưỡng40. Chữ ký APK trùng1.16. Chưa thay thế kiểm thử camera/GPU/codec trên điện thoại thật.

---

# Live Sân Đấu — 1.16.0 • Thử phiên manual mới từ đầu

Báo cáo A06 / 1.15: camera trả AE_OFF, exposure8.333.000 ns nhưng frame duration33.350.000 ns; cả callback và hình vào bộ lọc29,9 FPS. Đây là thất bại của lần thử60 trực tiếp, không phải bằng chứng60 đã chạy hay do đường120 còn tồn tại. Chưa đủ dữ liệu để kết luận do cấu hình app hay giới hạn HAL của thiết bị.

Thay đổi có giới hạn:
- Giữ bước đo sáng ngắn; sau đó đóng phiên AE và tạo phiên mới với TEMPLATE_MANUAL, AE_OFF, bỏ CONTROL_AE_TARGET_FPS_RANGE, đặt frame duration16.666.667 ns ngay từ request đầu tiên. Không dùng lại builder RECORD/AE30.
- Android28 trở lên truyền cùng request vào SessionConfiguration.setSessionParameters trước khi tạo phiên. Android chỉ áp dụng các khóa được camera công bố là session keys; bản này ghi rõ những khóa đó trong chẩn đoán. Không cam kết việc tạo phiên mới buộc HAL khởi động lại cảm biến.
- Bỏ qua callback hoàn tất/lỗi/mất buffer của phiên cũ sau khi chuyển phiên. Đặt lại cửa sổ đo FPS và thời gian chờ cho lần thử mới; chỉ xác nhận sau hai cửa sổ đạt ngưỡng95% ở cả callback và hình thực vào bộ lọc.
- Chẩn đoán tách Yêu cầu / Trả về, gồm AE range, frame duration, exposure và ISO; giữ báo cáo lần lỗi khi khôi phục cấu hình trước.
- Không dùng120 FPS, không thêm encoder/decoder trung gian, không nhân đôi khung hình. Giữ bảng điểm, bộ lọc, replay và khóa ký cập nhật. versionCode17/versionName1.16.0.

**Đây là bản thử nghiệm loại trừ ảnh hưởng phiên đo sáng cũ; chưa xác nhận60 FPS trên điện thoại thật.** Phơi sáng/ISO vẫn khóa sau đo sáng; đổi ánh sáng cần ÁP DỤNG lại khi đã dừng live. Nếu vẫn trả33,35 ms /30 FPS, thay đổi này không khắc phục được giới hạn đang gặp; không nên tiếp tục coi metadata60 là bằng chứng chạy được60.

Cách thử: cài cập nhật1.16, chọn camera sau1080p60 tại nơi đủ sáng rồi ÁP DỤNG. Chờ khoảng3–15 giây. Nếu không đạt, gửi phần cuối chẩn đoán từ “Đang dùng” và “Lần lỗi gần nhất”; cần đối chiếu Yêu cầu frame ns16666667 với Trả về và hai bộ đếm FPS. Có thể kiểm tra720p60 để phân biệt ảnh hưởng độ phân giải. Chỉ thử live riêng tư sau khi preview có hình chuyển động ổn định gần60; kiểm tra thêm FPS mã hóa, âm thanh, nhiệt và replay.

Kiểm tra bản1.16: assembleDebug thành công; 177 unit tests, 0 thất bại; lintDebug 0 lỗi/107 cảnh báo. Chữ ký APK trùng1.15. Chưa kiểm thử phiên Camera2 mới, GPU, codec và RTMP trên máy thật.

Đối chiếu API: https://developer.android.com/reference/android/hardware/camera2/params/SessionConfiguration#setSessionParameters(android.hardware.camera2.CaptureRequest)

---

# Live Sân Đấu — 1.15.0 • Camera trực tiếp 60 FPS

## Bản 1.15 — bỏ đường 120 → 60 theo yêu cầu

- Xóa HighSpeedVideoBridge và mã chọn/giãn khung 120 → 60. Không tạo phiên camera high-speed, không yêu cầu codec 120 FPS, không quét high-speed metadata để quyết định hỗ trợ 60. Bộ giải mã phục vụ replay cũ vẫn giữ nguyên.
- Đường hình: camera → texture GPU → bộ lọc thể thao/bảng điểm → encoder livestream ở FPS đã chọn. Không mã hóa/giải mã trung gian nguồn camera.
- Nếu camera công bố dải AE hợp lệ tới đúng FPS mục tiêu, dùng trực tiếp dải đó và giữ tự động phơi sáng.
- Nếu AE chỉ tới 30 nhưng camera công bố MANUAL_SENSOR, AE_OFF, các khóa SENSOR bắt buộc, phạm vi phơi sáng/ISO phù hợp và min frame duration <=16.666.667 ns cho kích thước nguồn: cho phép thử 60 trực tiếp. Không coi min frame duration đơn lẻ là bằng chứng đủ hỗ trợ.
- Nhánh manual đo sáng ngắn bằng dải AE đã được công bố (không quá30 FPS), lấy exposure/ISO thật từ CaptureResult, rồi đặt AE_OFF, SENSOR_FRAME_DURATION=16.666.667 ns, exposure tối đa8.333.333 ns, ISO bù trong khoảng hợp lệ. Tốc độ màn trập 1/120 giây KHÔNG phải quay 120 FPS. Không tự bịa dải AE [60,60] nếu camera không công bố.
- Giữ CONTROL_MODE_AUTO và lấy nét liên tục nếu camera hỗ trợ. Khi AE_OFF, hoạt động AF/AWB có thể khác theo thiết bị; phải kiểm tra hình thực tế. Phơi sáng/ISO khóa sau bước đo sáng, nên đổi điều kiện sáng cần dừng live rồi ÁP DỤNG lại. Chưa có tự động điều chỉnh độ sáng trong nhánh manual; thiếu sáng có thể tăng nhiễu hoặc tối khi ISO chạm giới hạn.
- Chỉ báo sẵn sàng sau hai cửa sổ đo khoảng một giây mà CẢ FPS callback cảm biến và số buffer mới vào bộ lọc đạt >=95% mục tiêu (60: >=57 FPS). Nhánh manual còn phải nhận được AE_OFF từ camera. Đây không phải kiểm tra nội dung sáng/tối của ảnh hay bảo đảm đúng60.000 FPS.
- Giữ giới hạn chờ khung hình/khởi tạo và khôi phục cấu hình trước khi thử thất bại. FPS thấp khi đang live vẫn cảnh báo, không tự dừng chỉ vì tụt FPS.
- Chẩn đoán bổ sung MANUAL_SENSOR, AE modes, khoảng ISO/exposure/max-frame, trạng thái yêu cầu và kết quả AE/frame-duration/exposure/ISO. Giữ snapshot của lần lỗi sau khi khôi phục, ghi rõ cấu hình đang chạy để tránh nhầm30 thành60.
- Giữ nguyên package, khóa ký debug, bảng điểm, filter, replay và các thiết lập kết nối; versionCode16, versionName1.15.0.

**Chưa thử trên Samsung A06 hoặc LG V40 thật.** Không bảo đảm máy chấp nhận manual60 chỉ vì có thông số phù hợp. Nếu MANUAL_SENSOR không được công bố hoặc camera vẫn trả30 FPS, bản này báo rõ; không chuyển sang120 FPS để thử tiếp. Không dùng trước khi kiểm tra cho buổi phát chính thức.

Cách thử:
1. Cài cập nhật APK1.15 lên bản cũ, mở camera sau ở nơi đủ sáng.
2. Chọn1080p60 rồi ÁP DỤNG, chờ khoảng3–12 giây.
3. Kiểm tra hình có chuyển động và FPS hình vào bộ lọc gần60. Nếu lỗi hoặc về30, sao chép Chẩn đoán camera/FPS; báo cáo giữ cả yêu cầu và số đo trước lỗi.
4. Khi preview ổn định, thử live riêng tư: kiểm tra FPS mã hóa, focus/màu sắc/độ sáng, âm thanh, bảng điểm và replay. Tránh cảnh thay đổi ánh sáng mạnh với nhánh phơi sáng khóa.

Kiểm tra tự động: assembleDebug thành công; 177 unit tests, 0 thất bại; lintDebug 0 lỗi (còn cảnh báo); chữ ký trùng 1.14. Các kiểm thử policy/phơi sáng không thay thế kiểm thử HAL, GPU và codec trên máy thật.

Đối chiếu API: https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#SENSOR_FRAME_DURATION

---

# Live Sân Đấu — 1.14.0 • Thử nghiệm sửa đường hình 60 FPS trên LG V40

## Bản 1.14 — thay đổi dựa trên báo cáo LG LM-V409N / Android 10

Báo cáo 1.13: camera sau ID 0 chỉ công bố AE thường tới 30 FPS; 1080p HIGH-SPEED có 120/240 FPS. App chọn 120–120 nhưng callback cảm biến chỉ đo 30.0 FPS; người dùng thấy nền camera đen, bảng tỉ số vẫn hiện. Encoder H.264 công bố hỗ trợ 1080p60. Đây không phải bằng chứng 60 FPS đã hoạt động, cũng không phải lỗi băng thông. 2K60 và camera trước60 không có tổ hợp phù hợp trong báo cáo này.

Thay đổi:
- Giữ đường camera thường cho 30 FPS và thiết bị công bố 60 FPS thông thường.
- Với nguồn high-speed, thay SurfaceTexture xem trước bằng Surface của bộ mã hóa H.264 phần cứng. Giải mã phần cứng về texture của RootEncoder, chọn khung thực theo timestamp để nhắm 60 FPS và giữ bộ lọc/bảng điểm/replay. Không tạo khung lặp, không đổi timestamp thành video quay chậm. Giãn thời điểm giao ảnh để tránh nhiều khung high-speed dồn cùng lúc bị SurfaceTexture bỏ qua.
- Chỉ dùng chế độ này khi máy công bố encoder và decoder phần cứng đủ độ phân giải/tốc độ. Khả năng chạy đồng thời nhiều codec vẫn phải thử trên máy thật.
- Đếm timestamp texture mới trên luồng GL, trước filter và bảng điểm; không thay listener SurfaceTexture của thư viện. Tách FPS callback cảm biến, FPS hình vào bộ lọc và FPS mã hóa khi live.
- Chỉ báo sẵn sàng sau hai cửa sổ đo khoảng một giây đạt >=85% FPS mục tiêu. Đây là kiểm tra buffer/FPS, không phải xác nhận nội dung ảnh sáng hay tối, và không khẳng định chính xác 60.000 FPS.
- Hết 8 giây không có hình mới hoặc quá 12 giây chưa xác nhận FPS thì báo lỗi. Nếu có cấu hình cũ, khôi phục cấu hình đó và ghi rõ chưa áp dụng lựa chọn mới. Khi đã live, FPS thấp được cảnh báo chứ không tự dừng chỉ vì thiếu sáng.
- Giữ đầy đủ CameraAccessException.reason, message, lỗi bộ đệm/codec; chờ callback đóng thiết bị trước khi giải phóng đầu ra. Chặn thông báo sẵn sàng do chọn lại spinner khi camera chưa được xác nhận.
- Giữ applicationId, khóa ký thử nghiệm, tính năng và các preset hiện có; versionCode 15, versionName 1.14.0.

**Giới hạn:** bản thử nghiệm chưa chạy trên LG V40 hoặc Samsung thật. Đường high-speed có thêm một lần nén/giải nén và đệm khoảng 50 ms, có thể tăng nhiệt, tốn pin, giảm chất lượng hoặc lệch âm/hình; mã hóa đồng thời có thể thất bại. Không cam kết đã sửa hết màn hình đen hoặc mở được 60 FPS trên mọi máy. Không ép 60 FPS ở camera/độ phân giải mà thiết bị không công bố hỗ trợ. Chưa kiểm thử GPU, micro, replay và RTMP đầu cuối trên thiết bị thật.

Cách thử trên LG V40:
1. Cài cập nhật APK 1.14, không gỡ ứng dụng cũ; xác nhận tên phiên bản trong Chẩn đoán camera.
2. Camera sau 1080p30: áp dụng, đợi có hình và trạng thái sẵn sàng.
3. Đổi 1080p60: áp dụng, đợi 3–12 giây. Nếu khôi phục 30 thì chưa áp dụng thành công 60.
4. Sao chép Chẩn đoán camera / FPS nếu đen, báo lỗi hoặc tốc độ thấp. Báo cáo không chứa Stream Key.
5. Chỉ sau khi xem trước ổn định, thử live riêng tư 5–10 phút: xem FPS mã hóa, hình có bảng điểm/replay, âm thanh, độ trễ và nhiệt. Không dùng ngay cho trận chính thức.

Kiểm tra tự động: assembleDebug, 171 unit tests (0 thất bại), lintDebug. Các test mới kiểm tra buffer lặp không tăng FPS, nguồn 30 không qua cổng 60, chọn 60 khung từ120 không đổi thời gian thực, và giãn các khung theo lô. Không thay thế kiểm thử camera/HAL/codec thật.

Tài liệu đối chiếu:
- https://developer.android.com/reference/android/hardware/camera2/CameraConstrainedHighSpeedCaptureSession
- https://developer.android.com/reference/android/media/MediaCodec

---

# Live Sân Đấu — 1.13.0 • Sửa chọn camera / 2K / 60 FPS

## Bản 1.13

Lỗi tìm thấy trong code 1.12: chỉ kiểm tra camera đầu tiên theo hướng trước/sau; coi metadata thời gian khung hình bằng 0 là không hỗ trợ 60 FPS; loại dải AE chứa FPS đích nhưng có trần lớn hơn; sửa AE request sau khi đã có khung đầu tiên. Đường Camera2Source của thư viện còn kiểm tra độ phân giải đầu ra riêng với độ phân giải nguồn đã chọn, gây bất nhất ở một số camera.

- Bộ chọn mới xét mọi camera công khai cùng hướng, chọn đúng ID, kích thước nguồn và dải FPS. Ưu tiên phiên thường, 60–60 nếu có, kích thước đủ nhỏ; vẫn từ chối giới hạn tốc độ đã được khai báo rõ và không phóng lớn 1080p thành 2K giả.
- Nguồn Camera2 riêng nhận SurfaceTexture trực tiếp: thiết lập dải FPS ngay từ request TEMPLATE_RECORD đầu tiên, không đợi khung hình rồi mới sửa. Kích thước camera khác kích thước H.264; ví dụ dùng 3840×2160 thu nhỏ về 2560×1440 nếu thiết bị công bố nguồn đó đủ FPS. Giữ tỷ lệ 16:9.
- Metadata thời gian bằng 0 là **chưa biết**, không tự kết luận chỉ 30 FPS. Dải AE vẫn phải công bố tốc độ đích. Thiết bị có thể nhận cấu hình nhưng chạy thấp hơn; UI báo FPS nguồn thực đo và FPS mã hóa riêng.
- Nếu không có phiên thường phù hợp, dùng constrained-high-speed đúng API khi camera công bố tổ hợp kích thước + FPS cố định tối đa 120. Nguồn 120 FPS được giới hạn xuất 60 bằng bỏ khung thật, không nội suy. Không tự bắt máy chạy 240 FPS. Chế độ này tốn pin/nóng hơn, zoom cố định 1× do giới hạn điều khiển phiên tốc độ cao; nguồn thường giữ zoom vuốt/phóng thu.
- Chờ camera trả khung đầu tiên trước khi kết nối live. Timeout camera 8 giây, hủy callback cũ theo phiên, thử khôi phục cấu hình cũ khi áp dụng cấu hình mới thất bại trước khi live. Lỗi khi đã live dừng phát thay vì âm thầm hạ cấu hình.
- ☰ → **Chẩn đoán camera / FPS** → Sao chép: model/Android, camera ID, AE ranges, độ phân giải nguồn, thời gian khung hình, high-speed và H.264 Surface. Không chứa URL, Stream Key, IMEI hoặc serial. Gửi báo cáo này khi vẫn bị từ chối để phân biệt metadata của hãng với lỗi mở phiên/bộ mã hóa.
- Giữ Facebook tối đa 1080p; kiểm tra 2K ở YouTube hoặc RTMP tùy chỉnh. Không đổi bitrate, bảng điểm, replay và bộ lọc của 1.12.

Chưa có điện thoại thật trong môi trường build: không khẳng định mọi máy hỗ trợ 2K/60 hoặc Samsung A06 đã chạy được 60 FPS. Camera mặc định của hãng có thể dùng chế độ không công khai cho Camera2. Chỉ báo "Sẵn sàng" sau callback khung hình, nhưng FPS/độ ổn định thực tế vẫn phải kiểm tra khi live riêng tư.

Kiểm tra cuối bản 1.13: assembleDebug, 160 unit tests (0 thất bại), lintDebug thành công. Đã xác minh versionCode 14 và chữ ký APK trùng bản 1.12. Các kiểm thử metadata và đường gọi không thay thế kiểm thử HAL, camera, GPU, micro và livestream trên máy thật.

Tài liệu API đối chiếu:
- https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap
- https://developer.android.com/reference/android/hardware/camera2/CameraConstrainedHighSpeedCaptureSession

## Bản 1.12

- Menu ☰ → **Bộ lọc thể thao**, hoặc trong cài đặt phát: Tắt / Nhẹ / Vừa. Mặc định Nhẹ; nhớ lựa chọn sau khi đóng app, dùng chung cho ba môn.
- Đổi bộ lọc ngay khi xem trước hoặc đang live, không phải bấm ÁP DỤNG; không khởi động lại camera, thay FPS/bitrate hay xóa replay.
- Một lượt GPU, năm mẫu ảnh cho mỗi điểm ảnh: giảm nhiễu không gian có giữ biên, tăng nét nhẹ giới hạn viền sáng, tương phản và độ bão hòa vừa phải. Không xử lý ảnh bằng CPU/Python, không AI, không trộn khung hình hay làm đẹp da.
- Thứ tự: camera → bộ lọc → bảng điểm/chữ chạy → replay → mã hóa. Bảng điểm/chữ chạy không bị làm mịn; replay giữ bộ lọc tại lúc ghi, không lọc chồng lần nữa. Đổi bộ lọc khi đang xem replay chỉ tác động hình camera, thấy khi trở về live.
- Tắt trả lại màu ảnh nguồn (vẫn có một lượt sao chép GPU của chuỗi filter). Nhẹ và Vừa đều dùng năm mẫu; mức Nhẹ giảm cường độ xử lý chứ không bảo đảm tải GPU thấp hơn Vừa.
- Không bổ sung chế độ camera 60 FPS, không nâng độ phân giải thật và không tạo FPS giả. Thiếu sáng, nén mạng và nguồn camera vẫn giới hạn chất lượng. Chưa đo nhiệt độ/FPS/chất lượng trên Samsung A06; nếu nóng hoặc tụt FPS, chọn Tắt và thử lại.
- Giữ nguyên package/khóa ký thử nghiệm để cập nhật APK cũ. Đây vẫn là bản thử nghiệm, chưa ký phát hành Google Play.

Kiểm tra bản 1.12: build APK thành công, 140 unit tests đạt; Android lint 0 lỗi, 101 cảnh báo. Đã xác minh versionCode 13 và chữ ký trùng APK trước. Kiểm thử gồm lưu/lấy preset, mức tham số, thứ tự filter, nhánh tắt và đường đổi preset không restart camera. Chưa chạy shader trên GPU điện thoại hay đo hiệu năng/live thực tế.

## Bản 1.11

- Chữ nhập và giá trị đang chọn: vàng nhạt `#FFE3A3`; nhãn/hướng dẫn: xanh nhạt `#9DDCCD`; gợi ý: xám xanh; nền ô nhập tối `#172534`. Nhãn đứng riêng, không chỉ phân biệt bằng màu. Ô bị khóa khi live chuyển xám.
- Áp dụng cho URL/key, tên đội, tên giải, thời gian, tiêu đề nghỉ, nội dung/nhãn chữ chạy và các danh sách lựa chọn. Cài đặt dùng giao diện tối nhất quán, kể cả khi điện thoại bật chế độ sáng.
- Tên hiển thị đổi thành **Live Sân Đấu**; thay biểu tượng nút phát đỏ bằng biểu tượng sân đấu dựng riêng. Giữ package và khóa ký thử nghiệm để cập nhật từ APK trước.
- Thay toàn bộ mẫu xanh ngọc bám ảnh tham khảo bằng **Contour • tím ngọc**. Khóa lưu `CLASSIC` được giữ để tự chuyển người dùng mẫu cũ sang Contour, không xóa điểm hay vị trí bảng. Điểm bóng đá vẫn sát nhau ở giữa hai tên đội.
- Bảng nghỉ, khung chữ chạy và bumper replay dùng bố cục vector mới. Giữ ba mẫu Arena/Minimal/Champion đã được dựng bằng mã hình học, không dùng logo đài/nền tảng hay ảnh đội bóng. Không đóng gói ảnh tham khảo, nhạc, video mẫu hoặc font tải ngoài.
- Menu → **Thông tin / giấy phép**: xem thông tin ứng dụng độc lập, nhắc quyền phát nội dung và giấy phép. Build tự gom danh sách thư viện runtime cùng LICENSE/NOTICE/COPYING nằm trong JAR/AAR; kèm Apache 2.0 và giấy phép MIT của SLF4J.

### Giới hạn trước khi đăng Google Play

Đây là **APK kiểm thử**, không phải bản đã được Google Play duyệt hay chứng nhận không vi phạm.
Không dùng khóa debug được chia sẻ trong gói nguồn để phát hành chính thức. Cần chuẩn bị quy trình ký phát hành,
kiểm tra tên/biểu tượng không gây nhầm lẫn, quyền đối với nội dung phát, giấy phép của từng phiên bản thư viện,
ảnh chụp cửa hàng từ bản thực tế, chính sách quyền riêng tư có thông tin liên hệ và khai báo Data safety chính xác.
Phần “Thông tin” hiện tại không thay thế chính sách quyền riêng tư hoàn chỉnh.

Đổi màu hoặc sửa tác phẩm của người khác không tự tạo quyền sử dụng. Quyền video, nhạc, giải đấu,
logo và quảng cáo do người vận hành đưa vào vẫn phải được kiểm tra riêng.

Nguồn chính thức đã đối chiếu:
- [Sở hữu trí tuệ](https://support.google.com/googleplay/android-developer/answer/9888072?hl=en)
- [Không giả mạo danh tính/quan hệ](https://support.google.com/googleplay/android-developer/answer/9888374?hl=en)
- [Quyền riêng tư](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)

Đã build APK 1.11.0, 131 unit tests đạt; Android lint 0 lỗi (còn cảnh báo). Đã kiểm tra ảnh vector
Contour, bảng nghỉ, khung chữ chạy, chuyển cảnh và biểu tượng. Kiểm tra tương phản chữ nhỏ đạt tối thiểu 4,5:1 trên các nền nhập/cài đặt tối.

Chưa kiểm thử camera, GPU, cảm ứng hoặc livestream trên điện thoại thật. Kiểm thử tự động và hình vector
xem trước không thay thế thử nghiệm thiết bị; thử live không công khai trước khi dùng cho trận đấu.

Các phần dưới là lịch sử phiên bản; giao diện/mẫu cũ được mô tả ở đó có thể đã được thay trong 1.11.

## Bản 1.10 — vào nhanh, chỉ hiện cài đặt liên quan

- Màn hình đầu chỉ chọn **bóng đá / bóng chuyền / pickleball** và **Facebook / YouTube / URL khác**, rồi bấm **VÀO CAMERA**. Không hỏi URL, key hoặc đặt lại trận ở bước này.
- Trong màn hình camera, bảng cài đặt mở sẵn để nhập key của đúng nền tảng. Facebook/YouTube ẩn URL mặc định; **URL khác** hiện cả URL và key. Nếu cần máy chủ riêng, menu có **URL [nền tảng] (nâng cao)** khi chưa live.
- Nút **Cài đặt [môn]** chỉ mở điều khiển của môn đang chọn. Bóng đá không hiện set/giao bóng; bóng chuyền không hiện số người giao của pickleball. Nhãn hiệp/set và bảng nghỉ cũng theo môn.
- Muốn đổi môn/nền tảng: quay về màn hình chọn. Đang live phải xác nhận dừng trước. Không tự phát khi vào camera; nhập key rồi bấm LIVE.
- Key không lưu qua lần mở lại/quay về màn hình chọn. Điểm trận hiện tại được giữ khi chọn lại cùng môn; nút **Trận mới** nằm trong cài đặt môn, có xác nhận. Hoàn tác không quay ngược sang phiên/môn trước.
- Facebook chỉ hiện lựa chọn chất lượng 720p/1080p; YouTube/URL khác có thêm 2K. Giữ các tính năng bảng điểm, chữ chạy, replay, zoom và menu thu gọn của 1.9.

APK 1.10.0 đã build; 122 unit tests qua, không có test thất bại.
Chưa kiểm thử bản này trên điện thoại thật. Hãy thử chọn từng môn/nền tảng, vào camera không key,
nhập key và live Không công khai; kiểm tra quay về chọn lại, điểm nhanh, replay và zoom trước khi phát chính thức.


## Bản 1.9

### Chọn mẫu bảng tỉ số

Menu → **Tỉ số / chữ chạy** → **Mẫu bảng tỉ số**. Mỗi môn bóng đá, bóng chuyền và pickleball
có 3 mẫu mới: **Arena • xanh đêm**, **Minimal • trắng tinh gọn**, **Champion • đen vàng**.
Giữ mẫu xanh ngọc cũ để có thể chọn lại. Mặc định mới là Arena; kiểu đã chọn lưu riêng cho từng môn.
Đổi kiểu không xóa điểm, set, đồng hồ hoặc vị trí/cỡ bảng. Có thể tiếp tục kéo/chụm đổi cỡ bảng
trong menu **Kéo vị trí / kích thước bảng**. Bảng lớn giữa hiệp giữ bố cục cũ.

Ba mẫu bóng đá mới đều đặt **tên đội A — điểm A : điểm B — tên đội B**, hai điểm sát nhau ở giữa;
đồng hồ/hiệp và tên giải nằm ở hàng phụ. Ba mẫu sân đấu tách rõ cột set và điểm, có dấu giao bóng.
Đồ họa tự thiết kế, không phải tài sản/logo của một nhà đài.

### Điểm nhanh trên màn hình

Với bóng chuyền/pickleball, khi hiện bảng tỉ số sẽ có hai nhóm **A − / +1** và **B − / +1**
ngay dưới hình camera, kèm tên và điểm. Bấm một lần đổi một điểm; không tự đổi set hoặc quyền giao bóng.
Nút điều khiển chỉ hiện trên điện thoại, không ghi vào video. Điểm cập nhật được lưu và có thể Hoàn tác
trong bảng điều khiển. Có thể tắt **Hiện nút ± điểm trên màn hình** trong Tỉ số / chữ chạy.
Ẩn nút khi chỉnh bố cục, mở cài đặt, replay, bảng giữa hiệp hoặc bảng điểm đang tắt.

### Zoom camera và nhãn replay

- Khi xem camera/đang live, **tách hai ngón để phóng to, chụm lại để thu nhỏ**.
- Đây là zoom camera được gửi tới người xem, không phải phóng riêng màn hình preview. Bảng điểm/chữ chạy
  giữ nguyên kích thước. Zoom số có thể làm hình kém nét hơn; giới hạn theo camera của điện thoại.
- Menu → **Zoom camera về 1×** để trở về góc nhìn thường. Camera khởi động lại sẽ trở về mức mặc định.
- Khi ở chế độ chỉnh bảng, chụm hai ngón chỉ đổi kích thước bảng. Khi đang replay hoặc mở cài đặt thì khóa zoom.
- Nhãn replay trên video đã đổi thành tab xanh đêm nhỏ **PHÁT LẠI**, không hiện 0,5×/0,25×.
  Tốc độ vẫn chọn trong Cài đặt replay; các chuyển cảnh 1.8 giữ nguyên.

### Kiểm tra trên máy thật

APK 1.9 đã build thành công, 113 unit tests qua, Android lint 0 lỗi. Ảnh 9 mẫu được dựng từ cùng
dữ liệu vector với ứng dụng; không phải ảnh chụp điện thoại, font thực tế có thể khác nhẹ.

Chưa kiểm thử thao tác cảm ứng, camera/GPU hoặc phát mạng trên điện thoại thật. Trước buổi phát chính thức,
thử live Không công khai: đổi cả 9 mẫu, đặt tên dài/điểm 3 chữ số, cộng trừ điểm nhanh, mở lại app,
zoom vào/ra và về 1×, chỉnh cỡ bảng, replay. Xác nhận các nút điều khiển không xuất hiện trên video nhận.


## Bản 1.8 — chạm REPLAY là chạy ngay

- Mở menu → **Cài đặt phát / replay** để chọn một lần: đoạn 3/5/8 giây, tốc độ 0,5×/0,25×
  và kiểu chuyển cảnh. Lựa chọn được giữ khi đóng/mở ứng dụng.
- Sau khi bắt đầu LIVE và bộ đệm đã đủ, chạm **REPLAY** sẽ phát ngay theo cấu hình đã lưu,
  không mở hộp thoại chọn nữa. Khi đang phát lại, nút đổi thành **VỀ LIVE**.
- Mặc định: **5 giây • 0,5× • Đồ họa thể thao**.

Ba kiểu chuyển cảnh:

1. **Cắt nhanh**: vào/ra replay tức thì, nhẹ nhất cho máy yếu.
2. **Hòa mờ điện ảnh**: hòa hình camera và replay trong 0,35 giây ở cả đầu và cuối.
3. **Đồ họa thể thao • quét chéo**: màn hình PHÁT LẠI / INSTANT REPLAY xanh ngọc–vàng,
   sau đó quét chéo vào pha bóng; cuối đoạn quét trở về camera.

Ảnh chuyển cảnh được vẽ vector bằng Canvas thành texture 1280×720 ngay trong ứng dụng,
không tải mạng và không thêm tệp video lớn. Hiệu ứng được ghép GPU vào cả preview và luồng phát.
98 unit tests đã qua; Android lint 0 lỗi. Chưa kiểm thử MediaCodec/OpenGL/camera/live trên điện thoại thật;
hãy thử ba kiểu ở 720p30 trong live Không công khai trước khi dùng cho trận đấu.


## Bản 1.7 — replay thử nghiệm

Cài cập nhật lên bản trước, giữ cùng package và khóa ký thử nghiệm.

1. Vào camera và bắt đầu LIVE như bình thường. Chờ khoảng 10 giây để nạp bộ đệm.
2. Bấm nút nhỏ **REPLAY** cạnh menu, hoặc menu → **Slowmotion / Phát lại**.
3. Chọn đoạn **3 / 5 / 8 giây vừa qua**, tốc độ **0,5× / 0,25×**.
   App báo nếu chưa đủ video; ví dụ đoạn 5 giây ở 0,5× phát trong khoảng 10 giây.
4. Khi hết đoạn, app tự trở về camera. Bấm **VỀ LIVE** để ngắt replay sớm.
   Việc chuyển hình không yêu cầu dừng/kết nối lại luồng mạng.

Replay xuất hiện trong hình gửi tới Facebook/YouTube/RTMP, có nhãn vàng **PHÁT LẠI**.
Các nút điều khiển và hộp thoại chỉ hiện trên điện thoại, không đưa lên sóng.
Giữ nguyên **âm thanh micro hiện tại** để bình luận, không tua lại âm thanh cũ.
Nếu micro đang tắt thì replay cũng không tự bật micro.
Bảng điểm và chữ chạy trong đoạn phát lại là hình đã ghi tại thời điểm đó, không phải điểm mới nhất.

### Giới hạn cần biết

- Chỉ nạp bộ đệm khi đang LIVE. Bộ đệm tạm dừng khi replay, xóa/nạp lại khi về camera;
  không thể lập tức replay tiếp pha xảy ra trong lúc đang phát lại.
- Video H.264 giữ tạm trong RAM tối đa 12 giây, 1.000 gói hình, 16 MiB trên máy ít RAM
  hoặc 32 MiB trên máy khác. Đây chỉ là giới hạn dữ liệu nén, không phải toàn bộ RAM ứng dụng.
  Độ dài thực tế có thể ngắn hơn tùy bitrate và khung hình khóa. Không lưu clip ra tệp.
- Phát chậm bằng cách kéo dài thời gian khung hình, không tạo thêm khung hình bằng AI.
  Nguồn 30 FPS ở 0,25× sẽ kém mượt; chọn 60 FPS chỉ khi điện thoại thực sự hỗ trợ.
- Cần thêm một bộ giải mã H.264 cùng với camera/bộ mã hóa đang chạy. Máy có thể thiếu tài nguyên
  ở 1080p60/2K. Nếu replay thất bại, app trở về LIVE và đề nghị thử 720p30.
- Không hỗ trợ phát lại từ tệp hoặc lưu nhiều đoạn đánh dấu trong phiên bản này.
- Rời ứng dụng vẫn dừng livestream như trước.

### Kiểm thử và cách thử an toàn

93 unit tests đã qua, gồm 21 bài mới về bộ đệm, khung hình khóa, giới hạn bộ nhớ,
timeline mã hóa, chọn đoạn và thời gian phát chậm. APK build thành công và Android lint không có lỗi.
Chưa kiểm thử camera, MediaCodec decoder, OpenGL hoặc luồng nhận trên điện thoại thật.
Đây là tính năng thử nghiệm: kiểm tra trong buổi live Không công khai trước khi dùng cho trận đấu.

- Bắt đầu với 720p30; quay vật đang chuyển động, chờ 10 giây, thử 5 giây ở 0,5×.
- Kiểm tra người xem thấy đúng đoạn cũ, nhãn PHÁT LẠI, hình không lật, micro vẫn là tiếng hiện tại.
- Thử VỀ LIVE giữa đoạn, tự kết thúc, chờ nạp lại và replay lần hai.
- Thử bật/tắt micro, chỉnh điểm trong lúc replay, dừng live và phát lại phiên mới.
- Sau khi ổn định mới thử 1080p/60 FPS; theo dõi FPS thực, nhiệt độ và trạng thái kết nối.

Kỹ thuật: tap dữ liệu từ RecordController của RootEncoder; giải mã ra SurfaceTexture và ghép GPU
sau lớp bảng điểm. Tham chiếu API: https://developer.android.com/reference/android/media/MediaCodec


## Bản 1.6 — giao diện đơn giản

1. Mở app → chọn **Bóng đá / Bóng chuyền / Pickleball**.
2. Chọn **Facebook / YouTube / URL khác**. Facebook và YouTube điền sẵn Server URL;
   thay bằng URL do nền tảng cấp nếu khác. Dán Stream Key vào ô riêng.
3. Bấm **VÀO CAMERA**: mở preview và tự bật mẫu bảng điểm của môn đã chọn, chưa phát ra mạng.
4. Bấm **LIVE** khi sẵn sàng. Chưa nhập key vẫn có thể vào camera xem thử; bấm LIVE sẽ mở cài đặt để bổ sung.

Cấu hình bắt đầu: YouTube/RTMP tùy chỉnh **720p30, 4 Mb/s**; Facebook **1080p30, 6 Mb/s**.
Không tự chọn 60 FPS hoặc tự hạ cấu hình khi máy không hỗ trợ. Muốn đổi: menu → **Cài đặt phát** → chọn → **ÁP DỤNG**.
Đổi FPS trong danh sách vẫn giữ camera chạy cấu hình cũ như bản 1.5.

Trên camera không còn hàng ba nút chữ dài ở phía trên. Chỉ còn nút **menu tròn 48 dp** ở góc phải;
phía dưới có nút LIVE (thu thành nút Dừng 48 dp khi phát) và dòng trạng thái ngắn.
Chạm dòng trạng thái để xem chi tiết. Các nút này và menu không xuất hiện trên video gửi đi.
Đang mở cài đặt thì chạm nút menu một lần để đóng gọn lại.

Menu gồm: **Tỉ số / chữ chạy**, **Kéo vị trí / kích thước bảng**, **Cài đặt phát / Ẩn cài đặt**,
**Bật/tắt micro**, **Đổi camera**, **Chọn lại môn / nơi phát**.
Khi chỉnh vị trí/cỡ bảng, menu ẩn và chỉ hiện nút **XONG**. Các thao tác kéo/chụm hai ngón vẫn như bản 1.5.
Nút Back đóng phần chỉnh/cài đặt trước; từ camera sẽ quay về màn hình chọn. Nếu đang live thì hỏi xác nhận dừng.
Nút Dừng cũng có xác nhận. Rời ứng dụng ra nền vẫn dừng live như các bản trước.

Không tự xóa điểm đã lưu: tích **Trận mới** trên màn hình chọn nếu muốn đặt điểm/set/đồng hồ về 0.
Tên đội, quảng cáo và vị trí/cỡ bảng được giữ. Mỗi lần vào camera, đồng hồ dừng để người điều khiển chủ động chạy.
Key không lưu vào Preferences/trạng thái Android; đổi nền tảng xóa key cũ. Chưa vào camera thì không khởi động camera/micro.

### Checklist thử bản 1.6

- Mở app thấy màn hình chọn, chưa hỏi quyền camera cho đến khi bấm VÀO CAMERA.
- Thử ba môn, ba đích; kiểm tra cấu hình mặc định và mẫu bảng tương ứng.
- Chưa có key → vẫn xem preview; LIVE → phải yêu cầu nhập key, không tự phát.
- Mở/ẩn cài đặt, kéo cỡ bảng, đóng bằng XONG; kiểm tra không còn hàng chữ lớn trên hình.
- Đổi FPS nhưng chưa áp dụng: camera tiếp tục chạy. Áp dụng cấu hình không hỗ trợ: báo rõ và giữ preview cũ.
- Đang live bấm Dừng/Back/Chọn lại: phải xác nhận; Hủy thì luồng tiếp tục.

Chưa kiểm thử giao diện/chạm, camera hoặc livestream trên điện thoại thật.
72 unit tests đã qua, gồm 11 bài mới về cấu hình bắt đầu nhanh, chọn môn,
giữ điểm đã lưu, chỉ reset khi yêu cầu, tạm dừng đồng hồ và giữ vị trí/kích thước bảng.
Đã kiểm tra cấu trúc XML: menu 48 dp, camera/cài đặt ẩn khi mở app, đủ lựa chọn,
hai ô key không lưu trạng thái và ô Trận mới không tự tích.

## Bản 1.5 — chỉnh bảng ngay trên camera, sửa đứng hình khi chọn FPS

Cài cập nhật trực tiếp lên bản 1.4, không cần xóa ứng dụng. Giữ nguyên package và khóa ký thử nghiệm.

1. Bấm **CHỈNH VỊ TRÍ / CỠ** phía trên hình camera. Bảng tỉ số được bật và phần cài đặt phát ẩn đi.
2. Kéo vào bảng để di chuyển. Kéo nút vàng góc dưới phải hoặc đặt hai ngón trên bảng rồi chụm/tách để thu/phóng.
3. Bấm **XONG • KHÓA BẢNG** để tránh chạm nhầm. Vị trí/cỡ được lưu riêng cho bóng đá, bóng chuyền,
   pickleball và bảng giữa hiệp. Có thể chỉnh khi đang live; người xem nhận thay đổi sau độ trễ phát.
4. **ĐẶT LẠI** chỉ trả vị trí/cỡ về mặc định, không xóa điểm/đồng hồ/quảng cáo.
   Có thể hoàn tác thao tác kéo bằng **Hoàn tác** trong **TỈ SỐ / CHỮ CHẠY**.

Bóng đá mặc định 60% kích thước đồ họa cũ (chiếm khoảng 35% chiều rộng video), bóng chuyền/pickleball 80%.
Giới hạn thu nhỏ là 30%; giới hạn phóng lớn phụ thuộc khung hình để bảng không ra ngoài video.
Bảng giữa hiệp chỉnh riêng; chữ chạy không đổi kích thước theo bảng điểm.
Khung vàng, nút kéo và hướng dẫn chỉ hiện trong app, không ghép vào luồng phát.
Preview giữ tỉ lệ 16:9, có viền đen nếu màn hình khác tỉ lệ, để vị trí kéo khớp với video gửi đi.

### Đổi FPS/chất lượng

Lỗi cũ: listener chọn FPS/độ phân giải gọi `dispose()` ngay, để lại khung hình cuối trong khi chờ bấm ÁP DỤNG.
Bản 1.5 không đóng camera khi chỉ chọn trong danh sách. App ghi rõ cấu hình đã chọn và cấu hình camera đang dùng.
Bấm **ÁP DỤNG / KIỂM TRA CAMERA** để đổi. Cấu hình được kiểm tra trước khi đóng camera cũ:
nếu 60 FPS/độ phân giải không hỗ trợ, preview cũ tiếp tục chạy và app báo chưa áp dụng.
Nếu khởi tạo cấu hình mới ném lỗi, app thử khôi phục cấu hình trước và không tự phát bằng cấu hình khác lựa chọn.
Khi thực sự áp dụng cấu hình được hỗ trợ, camera phải khởi động lại nên có thể ngắt hình ngắn.
Đang live vẫn khóa FPS/chất lượng; cần dừng live trước khi đổi.

### Thử trên điện thoại

- Đổi 30 → 60 trong danh sách mà chưa bấm ÁP DỤNG: hình phải tiếp tục chuyển động.
- Bấm ÁP DỤNG: máy hỗ trợ thì khởi động lại preview; máy không hỗ trợ phải báo lỗi và giữ preview cũ.
- Kéo bảng về bốn góc, thu/phóng, đổi môn rồi quay lại; đóng/mở app để kiểm tra lưu cỡ.
- Live không công khai: kiểm tra vị trí khớp preview và không thấy khung vàng/nút chỉnh trên video nhận.

Chưa kiểm thử thao tác chạm, GPU hoặc camera trên điện thoại thật. Bài kiểm tra tự động không thay thế
việc thử đổi FPS và livestream trên máy cụ thể.
61 unit tests đã qua, gồm 17 bài mới về thu/phóng, kéo góc, giới hạn khung hình,
lưu riêng bố cục trong trạng thái từng môn và ánh xạ preview 16:9.

## Bản 1.4 — thiết kế bảng tỉ số mới

Cài cập nhật trực tiếp lên bản 1.2/1.3, giữ nguyên package và khóa ký thử nghiệm.

- Bóng đá: thanh ngang xanh ngọc chuyển màu, hai ô điểm lớn, tên đội và đồng hồ/hiệp ở giữa.
- Bóng chuyền: bảng hai hàng, tách cột set thắng và điểm hiện tại; dấu đội giao bóng.
- Pickleball: cùng hệ thiết kế hai hàng, thêm dấu người giao bóng số 1/2.
- Bảng lớn giữa hiệp/giữa set và thanh quảng cáo dùng chung màu sắc, viền sáng và góc vát.
- Tên quá dài được thu nhỏ có giới hạn rồi rút gọn bằng dấu …, không thay đổi tên đã lưu.

Đây là thiết kế riêng theo phong cách truyền hình và ảnh tham chiếu của người dùng,
không phải đồ họa chính thức hay sản phẩm của VTV. Không gắn logo nhà đài.
Chọn môn trong **TỈ SỐ / CHỮ CHẠY** để tự đổi bố cục; các thao tác điều khiển vẫn như bản 1.3.

44 unit tests đã qua, gồm 13 bài kiểm tra thiết kế, giới hạn bố cục, tên dài và xuất ảnh mẫu.
Android lint: 0 lỗi, 41 cảnh báo. APK được build và kiểm tra chữ ký; chưa thử GPU/camera
hoặc phát lên YouTube/Facebook trên điện thoại thật. Ảnh xem trước được dựng từ cùng dữ liệu
vector với ứng dụng, không phải ảnh chụp điện thoại; font Android có thể khác nhẹ.

## Bản 1.3 — bảng điểm và chữ chạy

Bản này cập nhật trực tiếp lên 1.2, giữ nguyên package `com.vangnang.livecamera` và khóa ký debug.

1. Mở **TỈ SỐ / CHỮ CHẠY** ở góc màn hình.
2. Chọn **Bóng đá**, **Bóng chuyền** hoặc **Pickleball**. Nhập tên hai đội/người chơi,
   tên giải rồi bấm **Cập nhật tên đội / giải**. Bật **Hiện bảng tỉ số**.
3. Dùng **A +1 / A −1 / B +1 / B −1** để điều chỉnh điểm ngay trong lúc live.
4. Bóng chuyền/pickleball có số set thắng, đội giao bóng; pickleball có người giao số 1/2.
   Đây là bảng điểm điều khiển thủ công, không tự áp luật side-out, kết thúc set hay thắng trận.
   Nút **Set mới** chỉ đưa điểm về 0, không tự cộng set và không đổi nhãn set.
5. Nhập nhãn hiệp/set, ví dụ HIỆP 2 hoặc SET 3, rồi bấm cập nhật.
   Đồng hồ có chạy/dừng và đặt phút:giây (ví dụ 45:00); không tự chuyển hiệp.
6. Bật **Hiện bảng lớn giữa hiệp / giữa set** để hiện tỉ số lớn giữa hình.
   Đồng hồ tự dừng; tắt bảng giữa hiệp rồi bấm Chạy để tiếp tục.
7. Phần chữ chạy: nhập nội dung (tối đa 500 ký tự), nhãn QUẢNG CÁO/GIỚI THIỆU tùy ý,
   chọn chậm/vừa/nhanh → **Cập nhật nội dung chữ chạy** → **Bật chữ chạy**.
8. Đóng bảng điều khiển; **Ẩn cài đặt** giúp xem hình camera rộng hơn.

Đồ họa được ghép bằng bộ lọc OpenGL trước mã hóa nên có trong cả preview và video gửi tới
YouTube/Facebook. Chỉ bảng điểm và chữ chạy được ghép; các nút, bàn phím, key và hộp thoại không
bị đưa vào luồng. Đồ họa được thiết kế trên khung 1280×720 và co giãn theo video 16:9.
Chữ chạy cập nhật khoảng 30 lần/giây; tốc độ tính theo thời gian, không phụ thuộc FPS video.

**Hoàn tác** lưu 60 thao tác gần nhất trong phiên mở ứng dụng; hoàn tác sẽ để đồng hồ ở trạng thái
dừng. **Đổi bên** đổi cả tên, điểm, set và đội giao bóng. **Trận mới** yêu cầu xác nhận trước khi
xóa điểm/set/đồng hồ, giữ tên đội và quảng cáo. Cấu hình trận và chữ chạy được lưu; khi mở lại,
đồng hồ luôn dừng để bạn kiểm tra trước khi chạy. Rời ứng dụng vẫn dừng livestream như bản 1.2.

Khi mới cài, bảng điểm và chữ chạy đều tắt. Bạn cần bật từng phần muốn đưa lên sóng.
Thay đổi được cập nhật trong video ở phía điện thoại; người xem thấy sau độ trễ của nền tảng.

### Kiểm thử bản 1.3

31 unit tests kiểm tra cấu hình phát, URL, cộng/trừ điểm/set, đồng hồ, giữa hiệp, đổi bên,
reset trận và vị trí chữ chạy. Kiểm tra Android lint không có lỗi chặn build (còn cảnh báo).
Chưa kiểm thử GPU/camera/live trên điện thoại thật; cần kiểm tra chữ không lật, vị trí,
tiếng và FPS trên máy của bạn trước buổi phát chính thức.

Thử nhanh bằng buổi live Không công khai: bóng đá 1–0 → bảng giữa hiệp → chữ chạy;
đổi môn bóng chuyền/pickleball và thử set/giao bóng; bấm Hoàn tác; dừng/phát lại và mở lại app.

Ứng dụng thử nghiệm Android: camera + micro → một đích RTMP/RTMPS bằng Server URL và Stream Key.
Phần thu/mã hóa/phát dùng Kotlin và RootEncoder 2.7.2, không phải Python.

## Bản nâng cấp

- Chọn YouTube, Facebook hoặc đích RTMP tùy chỉnh.
- Chọn 720p, 1080p, 2K/QHD (2560×1440); chọn 30 hoặc 60 FPS.
- Facebook chặn cấu hình 2K; dùng 720p hoặc 1080p.
- Mặc định 1080p30. Không âm thầm hạ cấu hình nếu máy không hỗ trợ.
- Kiểm tra kích thước camera, thời gian khung hình tối thiểu, dải FPS Camera2 và khả năng mã hóa H.264.
- Nguồn camera lấy kích thước 16:9 bằng hoặc lớn hơn đầu ra, không phóng to nguồn nhỏ thành 2K.
- Ưu tiên dải FPS cố định khi camera công bố. Không dùng chế độ Camera2 constrained high-speed.
- Hiển thị FPS camera tính từ sensor timestamp và FPS đầu ra mã hóa do thư viện đo.
- Cảnh báo khi FPS mã hóa thấp hơn 85% mục tiêu trong 5 lần đo liên tiếp.
- Bảng điều khiển cuộn được; đổi camera trước/sau và bật/tắt micro.
- Không lưu key vào Preferences/trạng thái giao diện. Tắt log giao thức. Thông báo lỗi không chứa key.
- Khóa cấu hình khi đang phát. Dừng rồi đổi chất lượng/FPS/camera/nền tảng.
- Thử kết nối lại tối đa 5 lần. Rời ứng dụng hoặc mất surface sẽ dừng phát và giải phóng camera.

## Cài và dùng

APK là bản debug để thử, không phải bản phát hành Play Store. Gói mới `com.vangnang.livecamera`
cài song song bản cũ, biểu tượng tên **Live Camera YouTube FB**. Không cần xóa bản cũ.

1. Cấp quyền camera và micro, giữ điện thoại ngang.
2. Cuộn bảng điều khiển, chọn nền tảng trước; đổi nền tảng sẽ xóa key đang nhập.
3. Chọn độ phân giải và FPS → **ÁP DỤNG / KIỂM TRA CAMERA**.
4. Nếu báo không hỗ trợ, tự chọn 30 FPS hoặc độ phân giải thấp hơn. Có chế độ 60 FPS trong app camera gốc không có nghĩa Camera2 cho phép ứng dụng này dùng chế độ đó.
5. Lấy Server URL và Stream Key từ trang quản lý buổi live trên nền tảng; dán vào hai ô tương ứng.
6. Bấm **BẮT ĐẦU PHÁT**, kiểm tra trang quản lý live nhận hình/tiếng và bấm lên sóng nếu nền tảng yêu cầu.
7. Giữ ứng dụng mở và màn hình sáng. Bản này không hỗ trợ phát nền/tắt màn hình.

Facebook: tạo buổi live bằng phần mềm phát trong Live Producer. URL mặc định chỉ là gợi ý;
nếu Facebook cấp URL khác thì dùng đúng URL đó. Key có phần `?…&…` phải dán nguyên vẹn.
Không dán link xem video vào Server URL. Điều kiện tài khoản/trang do Facebook quyết định.

## Chuyển tiếp: phạm vi bản này

Đã hỗ trợ phương án **live camera bằng key cho Facebook**. Có thể gửi tới một dịch vụ
chuyển tiếp qua đích RTMP tùy chỉnh nếu người dùng có Server URL/key của dịch vụ đó.
Ứng dụng không tự lấy video từ link xem YouTube/Facebook, không tự tạo máy chủ chuyển tiếp,
và chưa phát đồng thời hai đích. Không đăng nhập tài khoản hay tự đăng công khai buổi live.

## Chất lượng và giới hạn

| Nền tảng | 720p30 / 60 | 1080p30 / 60 | 1440p30 / 60 |
|---|---|---|---|
| YouTube / RTMP tùy chỉnh | 4 / 6 Mb/s | 10 / 12 Mb/s | 15 / 24 Mb/s |
| Facebook | 4 / 4 Mb/s | 6 / 9 Mb/s | Không cho chọn khi phát |

H.264, keyframe mỗi 2 giây; AAC stereo 44.1 kHz 128 kb/s. RTMPS được ưu tiên.
Kiểm tra phần cứng chỉ xác nhận khả năng công bố, không bảo đảm tốc độ thực tế lâu dài.
Ánh sáng, nhiệt độ, thiết bị, mạng và nền tảng có thể giới hạn chất lượng/FPS.
FPS mã hóa khác với FPS YouTube/Facebook phân phối cho người xem. Khi thử 60 FPS,
theo dõi cả FPS camera và mã hóa; sau đó kiểm tra chất lượng 1080p60/1440p60 trên YouTube.
Không tạo thêm khung hình giả để hiển thị số 60.

## Build và kiểm tra

JDK 17 đầy đủ, Gradle 8.13, Android SDK 35 / build tools 35.0.0.
AGP 8.13.2, Kotlin 2.3.20. Mở bằng Android Studio hoặc dùng Gradle đã cài:

Thư mục `signing` chứa khóa debug chỉ dùng thử để các bản build tiếp theo cài cập nhật được.
Khóa này có mật khẩu chuẩn `android`, không được dùng làm khóa ký bản phát hành công khai.

```sh
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Kết quả APK: `app/build/outputs/apk/debug/app-debug.apk`.
Unit tests kiểm tra ma trận cấu hình, bitrate, kích thước QHD, ghép URL/key và chặn đầu vào sai.
Chưa kiểm thử phát thực tế bằng tài khoản YouTube/Facebook hay trên điện thoại của người dùng.

Checklist thử máy: cấp/từ chối quyền; 1080p30; 60 FPS trên máy hỗ trợ/không hỗ trợ;
1440p; FB chặn 1440p; đổi camera; mute/unmute; key sai; mất mạng; dừng/phát lại;
chuyển ứng dụng; kiểm tra key không bị giữ lại sau khi đóng tiến trình.

## Tài liệu

- YouTube encoder settings: https://support.google.com/youtube/answer/2853702
- Facebook Live specifications: https://www.facebook.com/business/help/162540111070395
- RootEncoder (Apache-2.0): https://github.com/pedroSG94/RootEncoder
