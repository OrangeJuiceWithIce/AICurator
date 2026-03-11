# AICurator
## 功能/逻辑介绍
1. 使用windowsAPI实现对NTFS文件系统的枚举，维护sqlite数据库  
2. 使用deepseekAPI进行对文件功能的分析  
3. 使用阿里云oss对被删除文件进行备份  
4. 支持日志回滚删除操作  
## 配置
在软件右上方，完成以下内容的配置以获取完整的功能  
1. 需要配置支持openai风格接口的大模型  
2. 需要自行配置阿里云oss的接口以支持文件备份  
## 项目结构
-app java代码部分  
  -lib jar文件  
  -src java代码实现部分  
-bin C编译后的dll文件  
-include C头文件部分  
-src C代码实现部分  

