package com.moneykk.moneytown.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignupRequest(@Schema(description = "로그인 이메일", example = "hong@example.com")
                            @NotBlank(message = "이메일은 필수입니다.")
                            @Email(message = "올바른 이메일 형식이 아닙니다.")
                            @Size(max = 255)
                            String email,

                            @Schema(description = "비밀번호", example = "Password123!")
                            @NotBlank(message = "비밀번호는 필수입니다.")
                            @Size(min = 8, max = 64,
                                    message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
                            String password,

                            @Schema(description = "사용자 이름", example = "홍길동")

                            @NotBlank(message = "이름은 필수입니다.")
                            @Size(max = 100)
                            String name,

                            @Schema(description = "휴대전화 번호", example = "010-1234-5678")
                            @NotBlank(message = "휴대전화 번호는 필수입니다.")
                            @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
                                    message = "올바른 휴대전화 번호 형식이 아닙니다.") String phone) {
}
