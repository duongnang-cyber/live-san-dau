# Live Sân Đấu 1.40.0

Mục **Môn thể thao và nơi phát** trong [Quản trị Live Sân Đấu](https://taode.online/admin/live-san-dau) cho phép tạo môn riêng, chọn một mẫu bảng điểm và đặt trạng thái hiển thị. Ứng dụng tải danh sách từ website khi mở và kiểm tra lại định kỳ trong lúc ứng dụng đang mở. Khi đổi cấu hình giữa trận, danh sách chọn môn được cập nhật cho lần vào camera tiếp theo.

- **Theo hiệp** dùng bảng điểm và đồng hồ của Bóng đá.
- **Theo set** dùng bảng điểm, số set và giao bóng của Bóng chuyền.
- **Theo set có người giao số 1/2** dùng bảng điểm của Pickleball.

Mỗi mẫu dùng các nút điều khiển điểm thủ công hiện có. Tên môn mới hiện trên màn hình phiên, bảng nghỉ và tiêu đề phát mặc định. Tên và lựa chọn môn đang dùng được lưu trên thiết bị.

## Tạo APK

1. Mở thư mục dự án bằng Android Studio có Android SDK Platform 35. Dùng Gradle 8.13 và JDK 17 cho bản dựng.
2. Cấu hình đường dẫn SDK bằng Android Studio hoặc `local.properties`.
3. Chạy `:app:testDebugUnitTest` và `:app:assembleDebug`. APK nằm tại `app/build/outputs/apk/debug/app-debug.apk`. Nếu dùng lệnh Gradle trực tiếp, chạy `gradle :app:testDebugUnitTest :app:assembleDebug`.
4. Cài thử APK đã build bằng cấu hình debug. Mã ứng dụng và khóa ký debug là của APK 1.38.2, mã phiên bản tăng từ 46 lên 47.

Có thể dùng workflow GitHub Actions kèm theo để build. Thiết lập secret `FACEBOOK_CLIENT_TOKEN` theo cấu hình Meta của ứng dụng. Client Token là giá trị phía Android, tuyệt đối không nhập App Secret vào bản dựng.

## Kiểm tra trên máy

1. Thêm “Cầu lông”, chọn mẫu **Theo set**, bật hiển thị và lưu trên website.
2. Mở ứng dụng 1.40.0, chọn Cầu lông và vào camera. Thử cộng điểm, số set và quyền giao bóng.
3. Tắt hiển thị “Cầu lông” trên website, kiểm tra nút sau khi mở lại app. Bật hiển thị lần nữa và kiểm tra nút trở lại.
4. Với máy đã ghép nối, thử logo PNG, ảnh phát lại, đăng nhập Facebook và phát thử trước khi dùng bản dựng cho trận thật.

Gói này được phát triển từ mã nguồn dựng lại 1.39.0. Gói mã nguồn không đi kèm APK đã biên dịch. Chưa kiểm chứng tương thích đầy đủ với bản nhị phân 1.38.2 trên điện thoại thật.
