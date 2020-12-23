package com.atguigu.crud.controller;

import com.atguigu.crud.bean.Employee;
import com.atguigu.crud.bean.Message;
import com.atguigu.crud.service.EmployeeService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 处理员工CRUD请求
 */
@Controller
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    /**
     * 员工查询（分页）
     *
     * @return
     */
    //@RequestMapping("/emps")
    public String getEmps(@RequestParam(value = "pn", defaultValue = "1") Integer pn,
                          Model model) {
        //引入PageHelper分页插件
        //在查询之前只需要调用，传入页码以及分页，每页大小
        PageHelper.startPage(pn, 5);

        List<Employee> emps = employeeService.getAll();
        //使用pageInfo包装查询的结果，只需要将pageInfo交给页面即可
        //封装了详细的分页信息，包括查询出来的数据,连续显示的页数
        PageInfo<Employee> page = new PageInfo(emps, 5);
        model.addAttribute("pageInfo", page);

        return "list";
    }

    /**
     * 员工查询（分页）
     *
     * @return
     */
    @RequestMapping("/emps")
    @ResponseBody
    public Message getEmpsWithJson(@RequestParam(value = "pn", defaultValue = "1") Integer pn) {
        //引入PageHelper分页插件
        //在查询之前只需要调用，传入页码以及分页，每页大小
        PageHelper.startPage(pn, 5);

        List<Employee> emps = employeeService.getAll();
        //使用pageInfo包装查询的结果，只需要将pageInfo交给页面即可
        //封装了详细的分页信息，包括查询出来的数据,连续显示的页数
        PageInfo<Employee> page = new PageInfo(emps, 5);

        return Message.success().add("pageInfo", page);
    }

    /**
     * 员工保存的方法
     *
     * @return
     */
    @RequestMapping(value = "/emps", method = RequestMethod.POST)
    @ResponseBody
    public Message saveEmp(@Valid Employee employee, BindingResult result) {

        if (result.hasErrors()) {
            List<FieldError> fieldErrors = result.getFieldErrors();
            Map<String, String> map = new HashMap<>();
            for (FieldError fieldError : fieldErrors) {
                System.out.println("错误的字段名：" + fieldError.getField());
                System.out.println("错误的信息：" + fieldError.getDefaultMessage());
                map.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
            return Message.fail().add("errorFiled", map);
        } else {
            employeeService.saveEmp(employee);
            return Message.success();
        }
    }

    /**
     * 检查用户名是否可用
     *
     * @return
     */
    @RequestMapping("/checkuser")
    @ResponseBody
    public Message checkUser(@RequestParam("empName") String empName) {

        //先判断用户名是否合法
        String regx = "(^[a-zA-Z0-9_-]{6,16}$)|(^[\u2E80-\u9FFF]{2,5})";
        if (!empName.matches(regx)) {
            return Message.fail().add("va_msg", "用户名必须是2-5位中文或者6-16位英文和数字的组合controller");
        }
        //数据库用户名重复校验
        boolean flag = employeeService.checkUser(empName);
        if (flag) {
            return Message.success();
        } else {
            return Message.fail().add("va_msg", "用户名不可用");
        }
    }

    /**
     * 根据员工id查询员工的数据
     *
     * @param id
     * @return
     */
    @RequestMapping(value = "/emp/{id}", method = RequestMethod.GET)
    @ResponseBody
    public Message getEmp(@PathVariable("id") Integer id) {

        Employee employee = employeeService.getEmp(id);
        return Message.success().add("emp", employee);
    }

    /**
     * 如果直接发送ajax=PUT形式的请求
     * 封装的数据
     * Employee
     * [empId=1014, empName=null, gender=null, email=null, dId=null]
     * <p>
     * 问题：
     * 请求体中有数据；
     * 但是Employee对象封装不上；
     * update tbl_emp  where emp_id = 1014;
     * <p>
     * 原因：
     * Tomcat：
     * 1、将请求体中的数据，封装一个map。
     * 2、request.getParameter("empName")就会从这个map中取值。
     * 3、SpringMVC封装POJO对象的时候。
     * 会把POJO中每个属性的值，request.getParamter("email");
     * AJAX发送PUT请求引发的血案：
     * PUT请求，请求体中的数据，request.getParameter("empName")拿不到
     * Tomcat一看是PUT不会封装请求体中的数据为map，只有POST形式的请求才封装请求体为map
     * org.apache.catalina.connector.Request--parseParameters() (3111);
     * <p>
     * protected String parseBodyMethods = "POST";
     * if( !getConnector().isParseBodyMethod(getMethod()) ) {
     * success = true;
     * return;
     * }
     * <p>
     * <p>
     * 解决方案；
     * 我们要能支持直接发送PUT之类的请求还要封装请求体中的数据
     * 1、配置上HttpPutFormContentFilter；
     * 2、他的作用；将请求体中的数据解析包装成一个map。
     * 3、request被重新包装，request.getParameter()被重写，就会从自己封装的map中取数据
     * 员工更新方法
     *
     * @param employee
     * @return
     */
    @RequestMapping(value = "/emp/{empId}", method = RequestMethod.PUT)
    @ResponseBody
    public Message saveEmp(Employee employee) {
        employeeService.updateEmp(employee);
        return Message.success();
    }

    /**
     * 单个批量二合一
     * 批量删除：1-2-3
     * 单个删除：1
     *
     * @param ids
     * @return
     */
    @RequestMapping(value = "/emp/{id}", method = RequestMethod.DELETE)
    public Message deleteEmp(@PathVariable("id") String ids) {
        if (ids.contains("-")) {
            //批量删除
            List<Integer> del_ids = new ArrayList<>();
            String[] str_ids = ids.split("-");
            //组装id的集合
            for (String str_id : str_ids) {
                del_ids.add(Integer.parseInt(str_id));
            }
            employeeService.deleteBatch(del_ids);

        } else {
            //单个删除
            employeeService.deleteEmpById(Integer.parseInt(ids));
        }
        return Message.success();
    }
}
